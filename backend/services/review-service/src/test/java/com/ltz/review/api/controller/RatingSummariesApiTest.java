package com.ltz.review.api.controller;

import com.ltz.review.application.port.GameCatalogPort;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;
import static com.ltz.review.api.controller.RatingApiTest.actor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class RatingSummariesApiTest {
    static final String PATH = "/api/v1/review/games/rating-summaries";
    @Autowired WebApplicationContext context;
    @Autowired JsonMapper json;
    @MockitoSpyBean JdbcClient jdbc;
    @MockitoBean GameCatalogPort games;
    MockMvc mvc;
    UUID user;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).defaultRequest(get("/").with(csrf())).apply(springSecurity()).build();
        user = UUID.randomUUID();
    }

    void seed(UUID game, int count) {
        jdbc.sql("""
                INSERT INTO game_ratings (id,user_id,game_id,rating,created_at,updated_at)
                SELECT gen_random_uuid(),gen_random_uuid(),?,((n % 10)+1)::smallint,now(),now()
                FROM generate_series(1,?) n
                """).params(game, count).update();
    }

    String body(List<UUID> ids) { return json.writeValueAsString(Map.of("gameIds", ids)); }

    @Test void preservesFirstOccurrenceOrderAndIncludesExplicitUnratedSummary() throws Exception {
        UUID first = UUID.randomUUID();
        UUID empty = UUID.randomUUID();
        UUID last = UUID.randomUUID();
        seed(first, 10);
        seed(last, 1);
        mvc.perform(post(PATH).with(actor(user)).contentType(MediaType.APPLICATION_JSON).content(body(List.of(first, empty, first, last))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].gameId").value(first.toString()))
                .andExpect(jsonPath("$.items[0].averageRating").value(5.5)).andExpect(jsonPath("$.items[0].ratingCount").value(10))
                .andExpect(jsonPath("$.items[1].gameId").value(empty.toString()))
                .andExpect(jsonPath("$.items[1].averageRating").isEmpty()).andExpect(jsonPath("$.items[1].ratingCount").value(0))
                .andExpect(jsonPath("$.items[2].gameId").value(last.toString()));
    }

    @Test void batchAndSingleUseIdenticalRoundingAndCounts() throws Exception {
        UUID game = UUID.randomUUID();
        seed(game, 3);
        var single = mvc.perform(get("/api/v1/review/games/" + game + "/rating-summary").with(actor(user)))
                .andReturn().getResponse().getContentAsString();
        var batch = mvc.perform(post(PATH).with(actor(user)).contentType(MediaType.APPLICATION_JSON).content(body(List.of(game))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(json.readTree(single), json.readTree(batch).get("items").get(0));
    }

    @Test void emptyListHasNoDatabaseOrHttpCalls() throws Exception {
        clearInvocations(jdbc);
        mvc.perform(post(PATH).with(actor(user)).contentType(MediaType.APPLICATION_JSON).content(body(List.of())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        verifyNoInteractions(jdbc, games);
    }

    @Test void rejectsInvalidListsAndUnknownFieldsWithoutQueryingDatabase() throws Exception {
        String tooMany = body(java.util.Collections.nCopies(51, UUID.randomUUID()));
        clearInvocations(jdbc);
        for (String invalid : List.of("{}", "{\"gameIds\":null}", "{\"gameIds\":[null]}", "{\"gameIds\":[\"invalid\"]}",
                "{\"gameIds\":[1]}", "{\"gameIds\":\"wrong\"}", "{\"gameIds\":[],\"userId\":\"forged\"}", tooMany)) {
            mvc.perform(post(PATH).with(actor(user)).contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        }
        verifyNoInteractions(jdbc, games);
    }

    @Test void fiftyGamesWithTenThousandRatingsRequireOneAggregateQuery() throws Exception {
        var ids = IntStream.range(0, 50).mapToObj(i -> UUID.randomUUID()).toList();
        for (UUID id : ids) seed(id, 200);
        clearInvocations(jdbc);
        mvc.perform(post(PATH).with(actor(user)).contentType(MediaType.APPLICATION_JSON).content(body(ids)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(50))
                .andExpect(jsonPath("$.items[0].ratingCount").value(200)).andExpect(jsonPath("$.items[49].averageRating").value(5.5));
        verify(jdbc, times(1)).sql(anyString());
        verifyNoInteractions(games);
    }

    @Test void concurrentReadersObserveConsistentAverageAndCountDuringWrites() throws Exception {
        UUID game = UUID.randomUUID();
        seed(game, 10);
        String request = body(List.of(game));
        try (var executor = Executors.newFixedThreadPool(4)) {
            Callable<Void> writer = () -> {
                for (int i = 0; i < 10; i++) {
                    jdbc.sql("INSERT INTO game_ratings (id,user_id,game_id,rating,created_at,updated_at) VALUES (?,?,?,10,now(),now())")
                            .params(UUID.randomUUID(), UUID.randomUUID(), game).update();
                }
                return null;
            };
            Callable<Void> reader = () -> {
                for (int i = 0; i < 5; i++) {
                    var response = mvc.perform(post(PATH).with(actor(user)).contentType(MediaType.APPLICATION_JSON).content(request))
                            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
                    var item = json.readTree(response).get("items").get(0);
                    long count = item.get("ratingCount").asLong();
                    var expected = java.math.BigDecimal.valueOf(55 + (count - 10) * 10)
                            .divide(java.math.BigDecimal.valueOf(count), 2, java.math.RoundingMode.HALF_UP);
                    assertEquals(0, expected.compareTo(new java.math.BigDecimal(item.get("averageRating").asText())));
                }
                return null;
            };
            for (var result : executor.invokeAll(List.of(writer, reader, reader, reader))) result.get();
        }
    }

    @Test void requiresAuthenticationAndEnforcesBodySizeLimit() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body(List.of())))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(PATH).with(actor(user)).contentType(MediaType.APPLICATION_JSON).content(" ".repeat(65537)))
                .andExpect(status().isPayloadTooLarge());
    }
}

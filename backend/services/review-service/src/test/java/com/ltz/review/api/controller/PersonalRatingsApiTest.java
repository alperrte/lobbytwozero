package com.ltz.review.api.controller;

import com.jayway.jsonpath.JsonPath;
import com.ltz.review.application.service.AccountErasureService;
import com.ltz.review.application.service.RatingWriter;
import com.ltz.review.infrastructure.persistence.repository.RatingRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static com.ltz.review.api.controller.PersonalRatingsController.PATH;
import static com.ltz.review.api.controller.RatingApiTest.actor;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class PersonalRatingsApiTest {
    @Autowired WebApplicationContext context;
    @Autowired JdbcClient jdbc;
    @Autowired RatingWriter writer;
    @Autowired AccountErasureService erasure;
    @MockitoSpyBean RatingRepository ratings;
    private final UUID owner = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();
    private MockMvc mvc;

    @BeforeEach void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach void removeFixtures() {
        jdbc.sql("DELETE FROM game_ratings WHERE user_id IN (?, ?)").params(owner, other).update();
    }

    @Test void usesOnlyVerifiedSubjectAndReturnsMinimalPrivatePayload() throws Exception {
        UUID game = UUID.randomUUID();
        writer.put(owner, game, 1);
        writer.put(other, game, 10);
        mvc.perform(get(PATH).with(actor(owner)).header("X-User-Id", other).header("X-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", allOf(containsString("private"), containsString("no-store"))))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].gameId").value(game.toString()))
                .andExpect(jsonPath("$.items[0].rating").value(1))
                .andExpect(jsonPath("$.items[0].updatedAt").value(org.hamcrest.Matchers.endsWith("Z")))
                .andExpect(jsonPath("$.items[0].length()").value(3))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()))
                .andExpect(jsonPath("$.truncated").value(false));
        mvc.perform(get(PATH).with(actor(other))).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].rating").value(10));
        mvc.perform(get(PATH).with(actor(owner)).param("userId", other.toString())).andExpect(status().isBadRequest());
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(get(PATH).with(jwt().jwt(token -> token.subject(owner.toString())))).andExpect(status().isForbidden());
    }

    @Test void keepsStableOrderAcrossEditsAndExcludesNewAndRemovedRowsFromContinuation() throws Exception {
        writer.put(owner, game(10), 1);
        writer.put(owner, game(30), 2);
        writer.put(owner, game(40), 3);
        writer.put(owner, game(50), 4);
        String cursor = cursor(mvc.perform(get(PATH).with(actor(owner)).param("size", "1"))
                .andExpect(jsonPath("$.items[0].gameId").value(game(10).toString())));
        writer.put(owner, game(10), 10);
        writer.put(owner, game(20), 9);
        writer.put(owner, game(40), 8);
        jdbc.sql("DELETE FROM game_ratings WHERE user_id = ? AND game_id = ?").params(owner, game(30)).update();
        mvc.perform(get(PATH).with(actor(other)).param("after", cursor)).andExpect(status().isBadRequest());
        mvc.perform(get(PATH).with(actor(owner)).param("after", cursor).param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].gameId").value(game(40).toString()))
                .andExpect(jsonPath("$.items[0].rating").value(8))
                .andExpect(jsonPath("$.items[1].gameId").value(game(50).toString()))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
        erasure.erase(owner);
        mvc.perform(get(PATH).with(actor(owner)).param("after", cursor))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
        mvc.perform(get(PATH).with(actor(owner))).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test void stopsAfterTenPagesAndReportsRemainingRatings() throws Exception {
        jdbc.sql("""
                INSERT INTO game_ratings (id, user_id, game_id, rating, created_at, updated_at)
                SELECT gen_random_uuid(), :owner, ('00000000-0000-0000-0000-' || lpad(to_hex(n), 12, '0'))::uuid,
                       7, now(), now() FROM generate_series(1, 501) n
                """).param("owner", owner).update();
        String cursor = null;
        for (int page = 1; page <= 10; page++) {
            var request = get(PATH).with(actor(owner));
            if (cursor != null) request.param("after", cursor);
            var result = mvc.perform(request).andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(50))
                    .andExpect(jsonPath("$.items[0].gameId").value(game(page * 50 - 49).toString()))
                    .andExpect(jsonPath("$.items[49].gameId").value(game(page * 50).toString()))
                    .andExpect(jsonPath("$.truncated").value(page == 10));
            cursor = cursor(result);
        }
        org.junit.jupiter.api.Assertions.assertNull(cursor);
    }

    @Test void rejectsMalformedAndRepeatedQueryParameters() throws Exception {
        for (String value : List.of("", "0", "51", "abc", "1.5")) {
            mvc.perform(get(PATH).with(actor(owner)).param("size", value)).andExpect(status().isBadRequest());
        }
        for (String value : List.of("", "!", "a".repeat(201), "dGVzdA")) {
            mvc.perform(get(PATH).with(actor(owner)).param("after", value))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_RATINGS_CURSOR"));
        }
        mvc.perform(get(PATH).with(actor(owner)).param("size", "1", "2")).andExpect(status().isBadRequest());
        mvc.perform(get(PATH).with(actor(owner)).param("after", "x", "y")).andExpect(status().isBadRequest());
    }

    @Test void returns503WithoutDatabaseDetailsOrSuccessfulEmptyResult() throws Exception {
        doThrow(new QueryTimeoutException("diagnostic")).when(ratings).readTime();
        mvc.perform(get(PATH).with(actor(owner))).andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("PERSONAL_RATINGS_UNAVAILABLE"))
                .andExpect(jsonPath("$.items").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("diagnostic"))));
    }

    private static UUID game(int value) {
        return new UUID(0, value);
    }

    private static String cursor(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.nextCursor");
    }
}

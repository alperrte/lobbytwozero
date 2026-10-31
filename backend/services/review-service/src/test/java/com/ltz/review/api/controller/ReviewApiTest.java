package com.ltz.review.api.controller;

import com.ltz.review.application.port.GameCatalogPort;
import com.ltz.review.domain.exception.ReviewException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;
import static com.ltz.review.api.controller.RatingApiTest.actor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Doğrulama ve eşzamanlı tekillik testleri tek kullanıcıyla çok istek attığından kota yükseltilir; kota AbuseQuotaApiTest'te. */
@SpringBootTest(properties = "ltz.review.abuse.review-create.limit=1000")
class ReviewApiTest {
    @Autowired WebApplicationContext context;
    @Autowired JdbcClient jdbc;
    @Autowired JsonMapper json;
    @MockitoBean GameCatalogPort games;
    MockMvc mvc;
    UUID user;
    UUID game;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).defaultRequest(get("/").with(csrf())).apply(springSecurity()).build();
        user = UUID.randomUUID();
        game = UUID.randomUUID();
    }

    String createPath() { return "/api/v1/review/games/" + game + "/reviews"; }

    @Test void createsTextWithoutRatingOrRecommendationAndReturnsLocation() throws Exception {
        var result = create("Deneme incelemesi").andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("Deneme incelemesi"))
                .andExpect(jsonPath("$.ownedByActor").value(true)).andExpect(jsonPath("$.version").value(0));
        String id = json.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asText();
        result.andExpect(header().string("Location", "/api/v1/review/reviews/" + id));
        mvc.perform(get("/api/v1/review/reviews/" + id).with(actor(UUID.randomUUID())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ownedByActor").value(false));
        assertEquals(0L, jdbc.sql("SELECT count(*) FROM game_ratings WHERE user_id=?").param(user).query(Long.class).single());
    }

    @Test void duplicateTextIsConflictAndDifferentUsersCanReviewSameGame() throws Exception {
        create("İlk metin").andExpect(status().isCreated());
        create("İkinci metin").andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
        mvc.perform(post(createPath()).with(actor(UUID.randomUUID())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"Başka yazar\"}")).andExpect(status().isCreated());
    }

    @Test void invalidBodiesAndMassAssignmentAreRejected() throws Exception {
        for (String body : List.of("", " ", "\n\t", "\u00a0", "a".repeat(5001), "\u0000bad")) create(body).andExpect(status().isBadRequest());
        for (String body : List.of("{}", "{\"body\":null}", "{\"body\":\"Test\",\"userId\":\"" + user + "\"}",
                "{\"body\":\"Test\",\"rating\":7}", "{\"body\":\"Test\",\"recommended\":true}"))
            mvc.perform(post(createPath()).with(actor(user)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        assertEquals(0L, jdbc.sql("SELECT count(*) FROM reviews WHERE user_id=?").param(user).query(Long.class).single());
    }

    @Test void maximumTextLengthIsAcceptedAndIdentityIsRequired() throws Exception {
        create("a".repeat(5000)).andExpect(status().isCreated());
        mvc.perform(post(createPath()).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Test\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/review/reviews/" + UUID.randomUUID()).with(actor(user))).andExpect(status().isNotFound());
    }

    @Test void unavailableOrUnreachableGamePreventsCreation() throws Exception {
        doThrow(new ReviewException(404, "Bulunamadı.")).when(games).requireAvailable(game);
        create("Test").andExpect(status().isNotFound());
        doThrow(new ReviewException(503, "Kesinti.")).when(games).requireAvailable(game);
        create("Test").andExpect(status().isServiceUnavailable());
        assertEquals(0L, jdbc.sql("SELECT count(*) FROM reviews WHERE user_id=?").param(user).query(Long.class).single());
    }

    @Test void concurrentCreatesHaveOneWinnerAndNoDuplicateRows() throws Exception {
        try (var executor = Executors.newFixedThreadPool(6)) {
            Callable<Integer> task = () -> create("Aynı anda").andReturn().getResponse().getStatus();
            var responses = executor.invokeAll(java.util.Collections.nCopies(12, task));
            int created = 0;
            for (var response : responses) {
                int code = response.get();
                if (code == 201) created++;
                else assertEquals(409, code);
            }
            assertEquals(1, created);
        }
        assertEquals(1L, jdbc.sql("SELECT count(*) FROM reviews WHERE user_id=?").param(user).query(Long.class).single());
    }

    @Test void onlyOwnerCanEditAndCreationTimeAndIndependentRatingArePreserved() throws Exception {
        String created = create("İlk metin").andReturn().getResponse().getContentAsString();
        var value = json.readTree(created);
        String path = "/api/v1/review/reviews/" + value.get("id").asText();
        jdbc.sql("INSERT INTO game_ratings (id,user_id,game_id,rating,created_at,updated_at) VALUES (?,?,?,7,now(),now())")
                .params(UUID.randomUUID(), user, game).update();
        mvc.perform(patch(path).with(actor(UUID.randomUUID())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0,\"body\":\"Saldırgan\"}")).andExpect(status().isForbidden());
        mvc.perform(patch(path).with(actor(user)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0,\"body\":\"Yeni metin\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.body").value("Yeni metin"))
                .andExpect(jsonPath("$.createdAt").value(value.get("createdAt").asText()));
        assertEquals(7, jdbc.sql("SELECT rating FROM game_ratings WHERE user_id=? AND game_id=?").params(user, game).query(Integer.class).single());
        mvc.perform(patch(path).with(actor(user)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0,\"body\":\"Eski sürüm\"}")).andExpect(status().isConflict());
    }

    @Test void patchRequiresExplicitNonNullBodyAndVersionAndRejectsOtherFields() throws Exception {
        String id = json.readTree(create("İlk metin").andReturn().getResponse().getContentAsString()).get("id").asText();
        String path = "/api/v1/review/reviews/" + id;
        for (String body : List.of("{}", "{\"version\":0}", "{\"version\":0,\"body\":null}", "{\"version\":-1,\"body\":\"Test\"}",
                "{\"body\":\"Test\"}", "{\"version\":0,\"body\":\"Test\",\"rating\":1}")) {
            mvc.perform(patch(path).with(actor(user)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        mvc.perform(patch(path).with(actor(user)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0,\"body\":\"İlk metin\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(0));
    }

    @Test void concurrentEditsWithSameVersionHaveOneWinner() throws Exception {
        String id = json.readTree(create("İlk metin").andReturn().getResponse().getContentAsString()).get("id").asText();
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> task = () -> mvc.perform(patch("/api/v1/review/reviews/" + id).with(actor(user))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0,\"body\":\"Yeni metin\"}"))
                    .andReturn().getResponse().getStatus();
            var results = executor.invokeAll(List.of(task, task));
            assertEquals(List.of(200, 409), results.stream().map(result -> {
                try { return result.get(); } catch (Exception error) { throw new IllegalStateException(error); }
            }).sorted().toList());
        }
    }

    @Test void deleteRequiresOwnerAndCurrentVersionIsIdempotentAndAllowsNewReview() throws Exception {
        String id = json.readTree(create("İlk metin").andReturn().getResponse().getContentAsString()).get("id").asText();
        String path = "/api/v1/review/reviews/" + id;
        jdbc.sql("INSERT INTO game_ratings (id,user_id,game_id,rating,created_at,updated_at) VALUES (?,?,?,7,now(),now())")
                .params(UUID.randomUUID(), user, game).update();
        mvc.perform(delete(path).param("version", "0").with(actor(UUID.randomUUID()))).andExpect(status().isForbidden());
        mvc.perform(delete(path).param("version", "1").with(actor(user))).andExpect(status().isConflict());
        mvc.perform(delete(path).with(actor(user))).andExpect(status().isBadRequest());
        mvc.perform(delete(path).param("version", "-1").with(actor(user))).andExpect(status().isBadRequest());
        mvc.perform(delete(path).param("version", "0").with(actor(user))).andExpect(status().isNoContent());
        mvc.perform(delete(path).param("version", "0").with(actor(user))).andExpect(status().isNoContent());
        mvc.perform(get(path).with(actor(user))).andExpect(status().isNotFound());
        mvc.perform(patch(path).with(actor(user)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":1,\"body\":\"Geri getir\"}")).andExpect(status().isNotFound());
        assertEquals(1L, jdbc.sql("SELECT count(*) FROM reviews WHERE id=? AND body IS NULL AND status='DELETED' AND version=1 AND deleted_at IS NOT NULL")
                .param(UUID.fromString(id)).query(Long.class).single());
        assertEquals(7, jdbc.sql("SELECT rating FROM game_ratings WHERE user_id=? AND game_id=?").params(user, game).query(Integer.class).single());
        create("Yeni inceleme").andExpect(status().isCreated());
        mvc.perform(delete("/api/v1/review/reviews/" + UUID.randomUUID()).param("version", "0").with(actor(user)))
                .andExpect(status().isNotFound());
    }

    @Test void deleteDoesNotDependOnGameAvailabilityAndConcurrentRepeatsAreSafe() throws Exception {
        String id = json.readTree(create("İlk metin").andReturn().getResponse().getContentAsString()).get("id").asText();
        doThrow(new ReviewException(503, "Kesinti.")).when(games).requireAvailable(game);
        try (var executor = Executors.newFixedThreadPool(4)) {
            Callable<Integer> task = () -> mvc.perform(delete("/api/v1/review/reviews/" + id).param("version", "0").with(actor(user)))
                    .andReturn().getResponse().getStatus();
            for (var result : executor.invokeAll(java.util.Collections.nCopies(8, task))) assertEquals(204, result.get());
        }
    }

    @Test void oversizedChunkedJsonIsRejectedBeforePersistenceAndResponsesAreNotCached() throws Exception {
        mvc.perform(post(createPath()).with(actor(user)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"" + "x".repeat(65536) + "\"}"))
                .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.status").value(413));
        create("Deneyim").andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "private, no-store"));
    }

    @Test void databaseRejectsWhitespaceAndInvalidReviewStates() {
        String insert = "INSERT INTO reviews (id,user_id,game_id,body,status,created_at,updated_at) VALUES (?,?,?,?,'VISIBLE',now(),now())";
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.sql(insert).params(UUID.randomUUID(), user, game, "\n\t ").update());
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.sql(insert).params(UUID.randomUUID(), user, game, "x".repeat(5001)).update());
    }

    org.springframework.test.web.servlet.ResultActions create(String body) throws Exception {
        return mvc.perform(post(createPath()).with(actor(user)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("body", body))));
    }
}

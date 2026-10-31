package com.ltz.review.api.controller;

import com.ltz.review.application.port.GameCatalogPort;
import com.ltz.review.application.service.AccountErasureService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class RecommendationApiTest {
    @Autowired WebApplicationContext context;
    @Autowired JdbcClient jdbc;
    @Autowired JsonMapper json;
    @Autowired AccountErasureService erasure;
    @Autowired com.ltz.review.infrastructure.persistence.repository.AccountWriteLock accounts;
    @Autowired com.ltz.review.infrastructure.config.ErasureGuardCleanup guardCleanup;
    @MockitoBean GameCatalogPort games;
    MockMvc mvc;
    UUID user;
    UUID game;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).defaultRequest(get("/").with(csrf())).apply(springSecurity()).build();
        user = UUID.randomUUID();
        game = UUID.randomUUID();
    }

    String gamePath() { return "/api/v1/review/games/" + game; }

    @Test void recommendationWithoutTextOrRatingCanBeReadRepeatedAndChanged() throws Exception {
        String first = recommend(true).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(0))
                .andReturn().getResponse().getContentAsString();
        assertEquals(first, recommend(true).andReturn().getResponse().getContentAsString());
        recommend(false).andExpect(status().isOk()).andExpect(jsonPath("$.recommended").value(false)).andExpect(jsonPath("$.version").value(1));
        mvc.perform(get(gamePath() + "/recommendation").with(actor(user))).andExpect(jsonPath("$.recommended").value(false));
        assertEquals(0L, jdbc.sql("SELECT count(*) FROM game_ratings WHERE user_id=?").param(user).query(Long.class).single());
        assertEquals(0L, jdbc.sql("SELECT count(*) FROM reviews WHERE user_id=?").param(user).query(Long.class).single());
    }

    @Test void ratingTextAndRecommendationRemainIndependentThroughEditAndDelete() throws Exception {
        mvc.perform(put(gamePath() + "/rating").with(actor(user)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":7}")).andExpect(status().isOk());
        mvc.perform(get(gamePath() + "/recommendation").with(actor(user))).andExpect(status().isNotFound());
        recommend(false).andExpect(status().isOk());
        String body = mvc.perform(post(gamePath() + "/reviews").with(actor(user)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"Deneyim\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String reviewPath = "/api/v1/review/reviews/" + json.readTree(body).get("id").asText();
        mvc.perform(patch(reviewPath).with(actor(user)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0,\"body\":\"Yeni deneyim\"}")).andExpect(status().isOk());
        recommend(true).andExpect(status().isOk());
        mvc.perform(get(reviewPath).with(actor(user))).andExpect(jsonPath("$.body").value("Yeni deneyim"));
        mvc.perform(delete(reviewPath).param("version", "1").with(actor(user))).andExpect(status().isNoContent());
        mvc.perform(get(gamePath() + "/rating").with(actor(user))).andExpect(jsonPath("$.rating").value(7));
        mvc.perform(get(gamePath() + "/recommendation").with(actor(user))).andExpect(jsonPath("$.recommended").value(true));
        erasure.erase(user);
        erasure.erase(user);
        for (String table : List.of("game_ratings", "reviews", "game_recommendations"))
            assertEquals(0L, jdbc.sql("SELECT count(*) FROM " + table + " WHERE user_id=?").param(user).query(Long.class).single());
    }

    @Test void invalidBodiesAnonymousAndOtherUserCannotChangeMyOpinion() throws Exception {
        for (String body : List.of("{}", "{\"recommended\":null}", "{\"recommended\":1}", "{\"recommended\":\"true\"}",
                "{\"recommended\":true,\"rating\":7}", "{\"recommended\":true,\"userId\":\"" + user + "\"}"))
            mvc.perform(put(gamePath() + "/recommendation").with(actor(user)).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        mvc.perform(put(gamePath() + "/recommendation").contentType(MediaType.APPLICATION_JSON).content("{\"recommended\":true}"))
                .andExpect(status().isUnauthorized());
        recommend(false).andExpect(status().isOk());
        UUID other = UUID.randomUUID();
        mvc.perform(get(gamePath() + "/recommendation").with(actor(other))).andExpect(status().isNotFound());
        mvc.perform(put(gamePath() + "/recommendation").with(actor(other)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"recommended\":true}")).andExpect(status().isOk());
        mvc.perform(get(gamePath() + "/recommendation").with(actor(user))).andExpect(jsonPath("$.recommended").value(false));
    }

    @Test void invalidGameAndDependencyFailureNeverCreateOpinion() throws Exception {
        doThrow(new ReviewException(404, "Bulunamadı.")).when(games).requireAvailable(game);
        recommend(true).andExpect(status().isNotFound());
        doThrow(new ReviewException(503, "Kesinti.")).when(games).requireAvailable(game);
        recommend(true).andExpect(status().isServiceUnavailable());
        assertEquals(0L, jdbc.sql("SELECT count(*) FROM game_recommendations WHERE user_id=?").param(user).query(Long.class).single());
    }

    @Test void concurrentRepeatedOpinionsAreUnique() throws Exception {
        try (var executor = Executors.newFixedThreadPool(6)) {
            Callable<Integer> task = () -> recommend(false).andReturn().getResponse().getStatus();
            for (var future : executor.invokeAll(java.util.Collections.nCopies(12, task))) assertEquals(200, future.get());
        }
        assertEquals(1L, jdbc.sql("SELECT count(*) FROM game_recommendations WHERE user_id=?").param(user).query(Long.class).single());
        mvc.perform(get(gamePath() + "/recommendation").with(actor(user))).andExpect(jsonPath("$.version").value(0));
    }

    @Test void erasureSerializesWithMixedWritesAndBlocksAllSubsequentWrites() throws Exception {
        try (var executor = Executors.newFixedThreadPool(4)) {
            Callable<Integer> rating = () -> mvc.perform(put(gamePath() + "/rating").with(actor(user)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"rating\":7}")).andReturn().getResponse().getStatus();
            Callable<Integer> recommendation = () -> recommend(true).andReturn().getResponse().getStatus();
            Callable<Integer> review = () -> mvc.perform(post(gamePath() + "/reviews").with(actor(user)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"body\":\"Deneyim\"}")).andReturn().getResponse().getStatus();
            Callable<Integer> erase = () -> { erasure.erase(user); return 204; };
            var results = executor.invokeAll(List.of(rating, recommendation, review, erase));
            for (int i = 0; i < 3; i++) {
                int code = results.get(i).get();
                org.junit.jupiter.api.Assertions.assertTrue(code == 403 || code == (i == 2 ? 201 : 200));
            }
            assertEquals(204, results.get(3).get());
        }
        recommend(true).andExpect(status().isForbidden());
        mvc.perform(put(gamePath() + "/rating").with(actor(user)).contentType(MediaType.APPLICATION_JSON).content("{\"rating\":7}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(gamePath() + "/reviews").with(actor(user)).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Tekrar\"}"))
                .andExpect(status().isForbidden());
        for (String table : List.of("game_ratings", "reviews", "game_recommendations"))
            assertEquals(0L, jdbc.sql("SELECT count(*) FROM " + table + " WHERE user_id=?").param(user).query(Long.class).single());
    }

    @Test void expiredErasureGuardIsCleanedWithoutRemovingLiveGuards() {
        String expired = "0".repeat(64);
        String active = "1".repeat(64);
        jdbc.sql("INSERT INTO review_erasure_guards (account_hash,expires_at) VALUES (?,now()-interval '1 minute'), (?,now()+interval '20 minutes')")
                .params(expired, active).update();
        guardCleanup.removeExpired();
        assertEquals(0L, jdbc.sql("SELECT count(*) FROM review_erasure_guards WHERE account_hash=?").param(expired).query(Long.class).single());
        assertEquals(1L, jdbc.sql("SELECT count(*) FROM review_erasure_guards WHERE account_hash=?").param(active).query(Long.class).single());
        jdbc.sql("DELETE FROM review_erasure_guards WHERE account_hash=?").param(active).update();
    }

    @Test void guardOperationsRequireAnExistingTransaction() {
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.transaction.IllegalTransactionStateException.class,
                () -> accounts.requireWritable(user));
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.transaction.IllegalTransactionStateException.class,
                () -> accounts.block(user));
    }

    org.springframework.test.web.servlet.ResultActions recommend(boolean recommended) throws Exception {
        return mvc.perform(put(gamePath() + "/recommendation").with(actor(user)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"recommended\":" + recommended + "}"));
    }
}

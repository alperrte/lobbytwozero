package com.ltz.review.api.controller;

import com.ltz.review.application.port.GameCatalogPort;
import com.ltz.review.application.service.AccountErasureService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static com.ltz.review.api.controller.RatingApiTest.actor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** REV-14: puan dağılımı, aktif inceleme sayısı ve tavsiye oranları; gerçek PostgreSQL 17. */
@SpringBootTest
class ReviewStatisticsApiTest {
    @Autowired WebApplicationContext context;
    @Autowired AccountErasureService erasure;
    @Autowired JsonMapper json;
    @MockitoSpyBean JdbcClient jdbc;
    @MockitoBean GameCatalogPort games;
    MockMvc mvc;
    UUID game;
    UUID user;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).defaultRequest(get("/").with(csrf())).apply(springSecurity()).build();
        game = UUID.randomUUID();
        user = UUID.randomUUID();
    }

    String path() { return "/api/v1/review/games/" + game + "/statistics"; }

    JsonNode stats() throws Exception {
        return json.readTree(mvc.perform(get(path()).with(actor(UUID.randomUUID()))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    void rating(UUID owner, int value) {
        jdbc.sql("INSERT INTO game_ratings (id,user_id,game_id,rating,created_at,updated_at) VALUES (?,?,?,?,now(),now())")
                .params(UUID.randomUUID(), owner, game, value).update();
    }

    void recommendation(UUID owner, boolean value) {
        jdbc.sql("INSERT INTO game_recommendations (id,user_id,game_id,recommended,created_at,updated_at) VALUES (?,?,?,?,now(),now())")
                .params(UUID.randomUUID(), owner, game, value).update();
    }

    void review(UUID owner, String status) {
        if ("VISIBLE".equals(status))
            jdbc.sql("INSERT INTO reviews (id,user_id,game_id,body,status,created_at,updated_at) VALUES (?,?,?,'Metin','VISIBLE',now(),now())")
                    .params(UUID.randomUUID(), owner, game).update();
        else
            jdbc.sql("INSERT INTO reviews (id,user_id,game_id,body,status,created_at,updated_at,deleted_at) VALUES (?,?,?,NULL,'DELETED',now(),now(),now())")
                    .params(UUID.randomUUID(), owner, game).update();
    }

    static long bucket(JsonNode stats, int rating) { return stats.get("ratings").get("distribution").get(rating - 1).get("userCount").asLong(); }

    @Test void emptyGameReturnsExplicitZeroDistributionAndNullRatiosWithoutCatalogCall() throws Exception {
        var result = stats();
        assertEquals(game.toString(), result.get("gameId").asString());
        assertEquals(0, result.get("ratings").get("ratingCount").asLong());
        assertTrue(result.get("ratings").get("averageRating").isNull());
        assertEquals(10, result.get("ratings").get("distribution").size());
        for (int rating = 1; rating <= 10; rating++) {
            assertEquals(rating, result.get("ratings").get("distribution").get(rating - 1).get("rating").asInt());
            assertEquals(0, bucket(result, rating));
        }
        assertEquals(0, result.get("reviews").get("activeCount").asLong());
        var recommendations = result.get("recommendations");
        assertEquals(0, recommendations.get("recommendedCount").asLong());
        assertEquals(0, recommendations.get("notRecommendedCount").asLong());
        assertEquals(0, recommendations.get("totalCount").asLong());
        assertTrue(recommendations.get("recommendedPercentage").isNull());
        assertTrue(recommendations.get("notRecommendedPercentage").isNull());
        verifyNoInteractions(games);
    }

    @Test void ratingsReviewsAndRecommendationsAreCountedSeparately() throws Exception {
        for (int i = 0; i < 3; i++) rating(UUID.randomUUID(), 7);
        rating(UUID.randomUUID(), 10);
        rating(UUID.randomUUID(), 1);
        rating(UUID.randomUUID(), 1);
        review(UUID.randomUUID(), "VISIBLE");
        review(UUID.randomUUID(), "VISIBLE");
        review(UUID.randomUUID(), "DELETED");
        for (int i = 0; i < 3; i++) recommendation(UUID.randomUUID(), true);
        recommendation(UUID.randomUUID(), false);
        var result = stats();
        assertEquals(6, result.get("ratings").get("ratingCount").asLong());
        assertEquals(2, bucket(result, 1));
        assertEquals(3, bucket(result, 7));
        assertEquals(1, bucket(result, 10));
        assertEquals(0, bucket(result, 5));
        assertEquals(0, new BigDecimal("5.5").compareTo(result.get("ratings").get("averageRating").decimalValue()));
        assertEquals(2, result.get("reviews").get("activeCount").asLong());
        assertEquals(3, result.get("recommendations").get("recommendedCount").asLong());
        assertEquals(1, result.get("recommendations").get("notRecommendedCount").asLong());
        assertEquals(4, result.get("recommendations").get("totalCount").asLong());
        assertEquals(0, new BigDecimal("75.00").compareTo(result.get("recommendations").get("recommendedPercentage").decimalValue()));
        assertEquals(0, new BigDecimal("25.00").compareTo(result.get("recommendations").get("notRecommendedPercentage").decimalValue()));
        assertFalse(result.has("userId"));
    }

    @Test void averageMatchesTheRatingSummaryEndpointForTheSameData() throws Exception {
        int[] values = {1, 1, 1, 1, 1, 1, 1, 2};
        for (int value : values) rating(UUID.randomUUID(), value);
        var summary = json.readTree(mvc.perform(get("/api/v1/review/games/" + game + "/rating-summary").with(actor(user)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var result = stats();
        assertEquals(0, summary.get("averageRating").decimalValue().compareTo(result.get("ratings").get("averageRating").decimalValue()));
        assertEquals(summary.get("ratingCount").asLong(), result.get("ratings").get("ratingCount").asLong());
        assertEquals(0, new BigDecimal("1.13").compareTo(result.get("ratings").get("averageRating").decimalValue()));
    }

    @Test void percentagesUseTwoDecimalsHalfUpOnTheRecommendationTotal() throws Exception {
        recommendation(UUID.randomUUID(), true);
        recommendation(UUID.randomUUID(), false);
        recommendation(UUID.randomUUID(), false);
        var thirds = stats().get("recommendations");
        assertEquals(0, new BigDecimal("33.33").compareTo(thirds.get("recommendedPercentage").decimalValue()));
        assertEquals(0, new BigDecimal("66.67").compareTo(thirds.get("notRecommendedPercentage").decimalValue()));
        game = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO game_recommendations (id,user_id,game_id,recommended,created_at,updated_at)
                SELECT gen_random_uuid(), gen_random_uuid(), ?, n = 1, now(), now() FROM generate_series(1, 800) AS n
                """).param(game).update();
        var tie = stats().get("recommendations"); // 0,125 → 0,13 ve 99,875 → 99,88 (HALF_UP)
        assertEquals(0, new BigDecimal("0.13").compareTo(tie.get("recommendedPercentage").decimalValue()));
        assertEquals(0, new BigDecimal("99.88").compareTo(tie.get("notRecommendedPercentage").decimalValue()));
    }

    @Test void ratingAndRecommendationChangesMoveCountsWithoutInflation() throws Exception {
        String ratePath = "/api/v1/review/games/" + game + "/rating";
        String recommendPath = "/api/v1/review/games/" + game + "/recommendation";
        for (int i = 0; i < 2; i++)
            mvc.perform(put(ratePath).with(actor(user)).contentType(MediaType.APPLICATION_JSON).content("{\"rating\":7}")).andExpect(status().isOk());
        var first = stats();
        assertEquals(1, first.get("ratings").get("ratingCount").asLong());
        assertEquals(1, bucket(first, 7));
        mvc.perform(put(ratePath).with(actor(user)).contentType(MediaType.APPLICATION_JSON).content("{\"rating\":3}")).andExpect(status().isOk());
        var moved = stats();
        assertEquals(1, moved.get("ratings").get("ratingCount").asLong());
        assertEquals(0, bucket(moved, 7));
        assertEquals(1, bucket(moved, 3));
        mvc.perform(put(recommendPath).with(actor(user)).contentType(MediaType.APPLICATION_JSON).content("{\"recommended\":true}")).andExpect(status().isOk());
        assertEquals(1, stats().get("recommendations").get("recommendedCount").asLong());
        mvc.perform(put(recommendPath).with(actor(user)).contentType(MediaType.APPLICATION_JSON).content("{\"recommended\":false}")).andExpect(status().isOk());
        var flipped = stats().get("recommendations");
        assertEquals(0, flipped.get("recommendedCount").asLong());
        assertEquals(1, flipped.get("notRecommendedCount").asLong());
        assertEquals(1, flipped.get("totalCount").asLong());
    }

    @Test void deletingTheTextReviewKeepsIndependentRatingAndRecommendation() throws Exception {
        mvc.perform(put("/api/v1/review/games/" + game + "/rating").with(actor(user)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":9}")).andExpect(status().isOk());
        mvc.perform(put("/api/v1/review/games/" + game + "/recommendation").with(actor(user)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"recommended\":true}")).andExpect(status().isOk());
        var created = mvc.perform(post("/api/v1/review/games/" + game + "/reviews").with(actor(user)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"Metin\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = json.readTree(created).get("id").asString();
        assertEquals(1, stats().get("reviews").get("activeCount").asLong());
        mvc.perform(delete("/api/v1/review/reviews/" + id).param("version", "0").with(actor(user))).andExpect(status().isNoContent());
        var after = stats();
        assertEquals(0, after.get("reviews").get("activeCount").asLong());
        assertEquals(1, after.get("ratings").get("ratingCount").asLong());
        assertEquals(1, bucket(after, 9));
        assertEquals(1, after.get("recommendations").get("recommendedCount").asLong());
    }

    @Test void accountErasureRemovesTheUsersContributionFromEveryStatistic() throws Exception {
        UUID other = UUID.randomUUID();
        rating(user, 4);
        rating(other, 4);
        recommendation(user, true);
        recommendation(other, false);
        review(user, "VISIBLE");
        review(other, "VISIBLE");
        assertEquals(2, bucket(stats(), 4));
        erasure.erase(user);
        erasure.erase(user);
        var after = stats();
        assertEquals(1, after.get("ratings").get("ratingCount").asLong());
        assertEquals(1, bucket(after, 4));
        assertEquals(1, after.get("reviews").get("activeCount").asLong());
        assertEquals(0, after.get("recommendations").get("recommendedCount").asLong());
        assertEquals(1, after.get("recommendations").get("notRecommendedCount").asLong());
        assertEquals(0, new BigDecimal("100.00").compareTo(after.get("recommendations").get("notRecommendedPercentage").decimalValue()));
    }

    @Test void largeDataIsAggregatedInOneSqlStatementWithoutPerRowQueries() throws Exception {
        jdbc.sql("""
                INSERT INTO game_ratings (id,user_id,game_id,rating,created_at,updated_at)
                SELECT gen_random_uuid(), gen_random_uuid(), ?, (n % 10) + 1, now(), now() FROM generate_series(1, 10000) AS n
                """).param(game).update();
        jdbc.sql("""
                INSERT INTO game_recommendations (id,user_id,game_id,recommended,created_at,updated_at)
                SELECT gen_random_uuid(), gen_random_uuid(), ?, n % 4 <> 0, now(), now() FROM generate_series(1, 4000) AS n
                """).param(game).update();
        clearInvocations(jdbc);
        var result = stats();
        verify(jdbc, times(1)).sql(anyString());
        verifyNoInteractions(games);
        assertEquals(10000, result.get("ratings").get("ratingCount").asLong());
        for (int rating = 1; rating <= 10; rating++) assertEquals(1000, bucket(result, rating));
        assertEquals(0, new BigDecimal("5.50").compareTo(result.get("ratings").get("averageRating").decimalValue()));
        assertEquals(3000, result.get("recommendations").get("recommendedCount").asLong());
        assertEquals(0, new BigDecimal("75.00").compareTo(result.get("recommendations").get("recommendedPercentage").decimalValue()));
    }

    @Test void concurrentWritersNeverProduceInternallyInconsistentResponses() throws Exception {
        var running = new AtomicBoolean(true);
        try (var executor = Executors.newFixedThreadPool(4)) {
            List<Callable<Boolean>> writers = new ArrayList<>();
            for (int writer = 0; writer < 2; writer++) writers.add(() -> {
                int written = 0;
                while (running.get() && written < 150) {
                    UUID owner = UUID.randomUUID();
                    rating(owner, 1 + written % 10);
                    recommendation(owner, written % 2 == 0);
                    written++;
                }
                return true;
            });
            var futures = writers.stream().map(executor::submit).toList();
            for (int read = 0; read < 60; read++) {
                var snapshot = stats();
                long distributed = 0;
                BigDecimal weighted = BigDecimal.ZERO;
                for (int rating = 1; rating <= 10; rating++) {
                    distributed += bucket(snapshot, rating);
                    weighted = weighted.add(BigDecimal.valueOf(rating * bucket(snapshot, rating)));
                }
                long count = snapshot.get("ratings").get("ratingCount").asLong();
                assertEquals(count, distributed);
                var recommendations = snapshot.get("recommendations");
                assertEquals(recommendations.get("recommendedCount").asLong() + recommendations.get("notRecommendedCount").asLong(),
                        recommendations.get("totalCount").asLong());
                var average = snapshot.get("ratings").get("averageRating");
                if (count == 0) assertTrue(average.isNull());
                else assertEquals(0, weighted.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP).compareTo(average.decimalValue()));
            }
            running.set(false);
            for (var future : futures) assertTrue(future.get());
        }
    }

    @Test void onlyAuthenticatedUsersCanReadAndInputIsValidated() throws Exception {
        mvc.perform(get(path())).andExpect(status().isUnauthorized());
        var guest = jwt().jwt(token -> token.subject(user.toString()).claim("roles", List.of("GUEST")))
                .authorities(new SimpleGrantedAuthority("ROLE_GUEST"));
        mvc.perform(get(path()).with(guest)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/review/games/not-a-uuid/statistics").with(actor(user))).andExpect(status().isBadRequest());
        mvc.perform(get(path()).with(actor(user))).andExpect(status().isOk()).andExpect(jsonPath("$.gameId").value(game.toString()))
                .andExpect(jsonPath("$.ratings.distribution.length()").value(10));
        mvc.perform(post(path()).with(actor(user)).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isMethodNotAllowed());
    }
}

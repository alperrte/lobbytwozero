package com.ltz.review.api.controller;

import com.ltz.review.application.port.GameCatalogPort;
import com.ltz.review.domain.exception.ReviewException;
import com.ltz.review.infrastructure.config.AbuseControlProperties;
import com.ltz.review.infrastructure.persistence.repository.AbuseQuotaRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;
import static com.ltz.review.api.controller.RatingApiTest.actor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** REV-13: onaylı varsayılan politika (inceleme 5/60 sn, beğeni+kaldırma 60/60 sn) kontrollü saatle doğrulanır. */
@SpringBootTest
class AbuseQuotaApiTest {
    static final Instant START = Instant.parse("2026-11-01T12:00:00Z"); // 60 sn pencere sınırı
    @Autowired WebApplicationContext context;
    @Autowired JdbcClient jdbc;
    @Autowired JsonMapper json;
    @Autowired AbuseControlProperties properties;
    @Autowired AbuseQuotaRepository quotas;
    @MockitoBean GameCatalogPort games;
    @MockitoBean Clock clock;
    final AtomicReference<Instant> now = new AtomicReference<>(START);
    MockMvc mvc;
    UUID user;

    @BeforeEach void setup() {
        now.set(START);
        when(clock.instant()).thenAnswer(invocation -> now.get());
        mvc = MockMvcBuilders.webAppContextSetup(context).defaultRequest(get("/").with(csrf())).apply(springSecurity()).build();
        user = UUID.randomUUID();
    }

    int createReview(UUID account, UUID game, String body) throws Exception {
        return mvc.perform(post("/api/v1/review/games/" + game + "/reviews").with(actor(account))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("body", body))))
                .andReturn().getResponse().getStatus();
    }

    UUID createdReview(UUID account) throws Exception {
        var response = mvc.perform(post("/api/v1/review/games/" + UUID.randomUUID() + "/reviews").with(actor(account))
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Deneyim\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(response).get("id").asText());
    }

    String like(UUID account, UUID review, boolean add) throws Exception {
        var request = add ? put("/api/v1/review/reviews/" + review + "/like") : delete("/api/v1/review/reviews/" + review + "/like");
        var response = mvc.perform(request.with(actor(account))).andReturn().getResponse();
        return response.getStatus() + ":" + response.getHeader("Retry-After");
    }

    Integer attempts(UUID account, String action) {
        return jdbc.sql("SELECT sum(attempts) FROM review_rate_limits WHERE account_hash = ? AND action = ?")
                .param(hash(account)).param(action).query(Integer.class).single();
    }

    long counterRows(UUID account) {
        return jdbc.sql("SELECT count(*) FROM review_rate_limits WHERE account_hash = ?").param(hash(account)).query(Long.class).single();
    }

    static String hash(UUID account) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(account.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Test void approvedDefaultLimitsAreBoundFromCentralConfiguration() {
        assertEquals(5, properties.reviewCreate().limit());
        assertEquals(Duration.ofSeconds(60), properties.reviewCreate().window());
        assertEquals(60, properties.reviewLike().limit());
        assertEquals(Duration.ofSeconds(60), properties.reviewLike().window());
    }

    @Test void sixthReviewInWindowGets429WithRetryAfterAndNothingIsCreated() throws Exception {
        for (int i = 0; i < 5; i++) assertEquals(201, createReview(user, UUID.randomUUID(), "Deneyim " + i));
        mvc.perform(post("/api/v1/review/games/" + UUID.randomUUID() + "/reviews").with(actor(user))
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Altıncı\"}"))
                .andExpect(status().isTooManyRequests()).andExpect(header().stringValues("Retry-After", "60"))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(429)).andExpect(jsonPath("$.title").value("Too Many Requests"));
        assertEquals(5L, jdbc.sql("SELECT count(*) FROM reviews WHERE user_id = ?").param(user).query(Long.class).single());
        verify(games, times(5)).requireAvailable(any()); // engellenen 6. istek denetçi/servis katmanına ulaşmadı
        assertEquals(201, createReview(UUID.randomUUID(), UUID.randomUUID(), "Başka kullanıcı"));
    }

    @Test void retryAfterShrinksWithTheWindowAndNewWindowRestoresTheQuota() throws Exception {
        for (int i = 0; i < 5; i++) assertEquals(201, createReview(user, UUID.randomUUID(), "Deneyim"));
        now.set(START.plusMillis(30_500));
        mvc.perform(post("/api/v1/review/games/" + UUID.randomUUID() + "/reviews").with(actor(user))
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Erken\"}"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "30"));
        now.set(START.plusMillis(59_999));
        mvc.perform(post("/api/v1/review/games/" + UUID.randomUUID() + "/reviews").with(actor(user))
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Son an\"}"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "1"));
        now.set(START.plusSeconds(60));
        for (int i = 0; i < 5; i++) assertEquals(201, createReview(user, UUID.randomUUID(), "Yeni pencere " + i));
        assertEquals(429, createReview(user, UUID.randomUUID(), "Yeni pencere aşımı"));
    }

    @Test void duplicateAndBusinessInvalidCreateAttemptsConsumeQuota() throws Exception {
        UUID game = UUID.randomUUID();
        assertEquals(201, createReview(user, game, "İlk"));
        for (int i = 0; i < 4; i++) assertEquals(409, createReview(user, game, "Tekrar"));
        assertEquals(429, createReview(user, game, "Tekrar"));
        assertEquals(6, attempts(user, "REVIEW_CREATE")); // limit + 1'de durur
        UUID invalid = UUID.randomUUID();
        for (int i = 0; i < 5; i++) assertEquals(400, createReview(invalid, UUID.randomUUID(), "   "));
        assertEquals(429, createReview(invalid, UUID.randomUUID(), "Geçerli ama kota doldu"));
    }

    @Test void failedBusinessTransactionsDoNotRollBackQuotaConsumption() throws Exception {
        doThrow(new ReviewException(503, "Katalog yok")).when(games).requireAvailable(org.mockito.ArgumentMatchers.any());
        assertEquals(503, createReview(user, UUID.randomUUID(), "Katalog kesintisi"));
        assertEquals(1, attempts(user, "REVIEW_CREATE"));
        assertEquals(0L, jdbc.sql("SELECT count(*) FROM reviews WHERE user_id = ?").param(user).query(Long.class).single());
    }

    @Test void likeAndUnlikeShareOneQuotaAndReadsStayUnlimited() throws Exception {
        UUID review = createdReview(UUID.randomUUID());
        UUID voter = UUID.randomUUID();
        for (int i = 0; i < 30; i++) {
            assertEquals("200:null", like(voter, review, true));
            assertEquals("200:null", like(voter, review, false));
        }
        assertEquals("429:60", like(voter, review, true));
        assertEquals("429:60", like(voter, review, false));
        // Engellenen PUT servise ulaşsaydı oy eklenirdi; 30 PUT + 30 DELETE sonrası oy yok.
        mvc.perform(get("/api/v1/review/reviews/" + review + "/like").with(actor(voter)))
                .andExpect(jsonPath("$.likedByActor").value(false)).andExpect(jsonPath("$.likeCount").value(0));
        for (int i = 0; i < 100; i++)
            mvc.perform(get("/api/v1/review/reviews/" + review + "/like").with(actor(voter))).andExpect(status().isOk());
        assertEquals("200:null", like(UUID.randomUUID(), review, true));
        assertEquals(61, attempts(voter, "REVIEW_LIKE"));
    }

    @Test void selfLikeAndMissingTargetAttemptsAreCounted() throws Exception {
        UUID review = createdReview(user);
        assertEquals("403:null", like(user, review, true));
        assertEquals("404:null", like(user, UUID.randomUUID(), true));
        assertEquals(2, attempts(user, "REVIEW_LIKE"));
    }

    @Test void windowRenewalRestoresTheLikeQuota() throws Exception {
        UUID review = createdReview(UUID.randomUUID());
        UUID voter = UUID.randomUUID();
        for (int i = 0; i < 60; i++) assertEquals("200:null", like(voter, review, true));
        now.set(START.plusMillis(45_250));
        assertEquals("429:15", like(voter, review, true));
        now.set(START.plusSeconds(60));
        assertEquals("200:null", like(voter, review, true));
    }

    @Test void actionsAndAccountsHaveIndependentQuotas() throws Exception {
        for (int i = 0; i < 5; i++) assertEquals(201, createReview(user, UUID.randomUUID(), "Deneyim"));
        assertEquals(429, createReview(user, UUID.randomUUID(), "Aşım"));
        UUID review = createdReview(UUID.randomUUID());
        assertEquals("200:null", like(user, review, true));
        assertEquals(201, createReview(UUID.randomUUID(), UUID.randomUUID(), "Başka hesap"));
        mvc.perform(put("/api/v1/review/games/" + UUID.randomUUID() + "/rating").with(actor(user))
                .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":7}")).andExpect(status().isOk());
        assertEquals(0L, jdbc.sql("SELECT count(*) FROM review_rate_limits WHERE action NOT IN ('REVIEW_CREATE','REVIEW_LIKE')")
                .query(Long.class).single());
    }

    @Test void anonymousAndUnauthorizedRequestsKeepSecurityStatusesWithoutConsumingQuota() throws Exception {
        UUID review = createdReview(UUID.randomUUID());
        mvc.perform(post("/api/v1/review/games/" + UUID.randomUUID() + "/reviews").contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"Anonim\"}")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/review/reviews/" + review + "/like")).andExpect(status().isUnauthorized());
        var guest = jwt().jwt(token -> token.subject(user.toString()).claim("roles", List.of("GUEST")))
                .authorities(new SimpleGrantedAuthority("ROLE_GUEST"));
        mvc.perform(post("/api/v1/review/games/" + UUID.randomUUID() + "/reviews").with(guest)
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Yetkisiz\"}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/review/reviews/" + review + "/like").with(guest)).andExpect(status().isForbidden());
        assertEquals(0L, counterRows(user));
    }

    @Test void concurrentReviewCreationsAreCountedExactlyAcrossThreads() throws Exception {
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Callable<Integer>> jobs = new ArrayList<>();
            for (int i = 0; i < 16; i++) jobs.add(() -> createReview(user, UUID.randomUUID(), "Eşzamanlı"));
            int created = 0;
            int limited = 0;
            for (var result : executor.invokeAll(jobs)) {
                int code = result.get();
                if (code == 201) created++;
                else if (code == 429) limited++;
            }
            assertEquals(5, created);
            assertEquals(11, limited);
        }
        assertEquals(6, attempts(user, "REVIEW_CREATE"));
        assertEquals(5L, jdbc.sql("SELECT count(*) FROM reviews WHERE user_id = ?").param(user).query(Long.class).single());
    }

    @Test void concurrentLikesAreCountedExactlyAcrossThreads() throws Exception {
        UUID review = createdReview(UUID.randomUUID());
        UUID voter = UUID.randomUUID();
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Callable<String>> jobs = new ArrayList<>();
            for (int i = 0; i < 75; i++) jobs.add(() -> like(voter, review, true));
            int accepted = 0;
            int limited = 0;
            for (var result : executor.invokeAll(jobs)) {
                String outcome = result.get();
                if (outcome.startsWith("200")) accepted++;
                else if ("429:60".equals(outcome)) limited++;
            }
            assertEquals(60, accepted);
            assertEquals(15, limited);
        }
        mvc.perform(get("/api/v1/review/reviews/" + review + "/like").with(actor(voter)))
                .andExpect(jsonPath("$.likeCount").value(1)).andExpect(jsonPath("$.likedByActor").value(true));
    }

    @Test void cleanupRemovesOnlyRowsExpiredBeyondRetention() {
        String hash = hash(user);
        jdbc.sql("""
                INSERT INTO review_rate_limits (account_hash, action, window_start, expires_at, attempts) VALUES
                (:h, 'REVIEW_CREATE', now() - interval '20 minutes', now() - interval '19 minutes', 1),
                (:h, 'REVIEW_LIKE', now() - interval '2 minutes', now() - interval '1 minute', 1),
                (:h, 'REVIEW_CREATE', now(), now() + interval '1 minute', 1)
                """).param("h", hash).update();
        assertTrue(quotas.removeExpired() >= 1); // paylaşılan test DB'sinde başka eski satırlar da silinebilir
        assertEquals(2L, counterRows(user));
        assertEquals(0L, jdbc.sql("SELECT count(*) FROM review_rate_limits WHERE account_hash = ? AND window_start < now() - interval '10 minutes'")
                .param(hash).query(Long.class).single());
    }

    void insertCounter(String accountHash, String action, int attempts, String lifetime) {
        jdbc.sql("INSERT INTO review_rate_limits (account_hash, action, window_start, expires_at, attempts) VALUES (?, ?, now(), now() + ?::interval, ?)")
                .param(accountHash).param(action).param(lifetime).param(attempts).update();
    }

    @Test void counterTableRejectsInvalidRowsAndStoresNoRawAccountId() {
        String valid = hash(user);
        String rawId = user.toString();
        assertThrows(DataIntegrityViolationException.class, () -> insertCounter(rawId, "REVIEW_LIKE", 1, "1 minute"));
        assertThrows(DataIntegrityViolationException.class, () -> insertCounter(valid, "review-like", 1, "1 minute"));
        assertThrows(DataIntegrityViolationException.class, () -> insertCounter(valid, "REVIEW_LIKE", 0, "1 minute"));
        assertThrows(DataIntegrityViolationException.class, () -> insertCounter(valid, "REVIEW_LIKE", 1, "0 minutes"));
        var columns = jdbc.sql("SELECT column_name FROM information_schema.columns WHERE table_name = 'review_rate_limits'")
                .query(String.class).list();
        assertTrue(columns.containsAll(List.of("account_hash", "action", "window_start", "expires_at", "attempts")));
        assertEquals(5, columns.size());
    }
}

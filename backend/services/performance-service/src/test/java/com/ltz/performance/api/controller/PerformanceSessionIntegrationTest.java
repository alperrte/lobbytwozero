package com.ltz.performance.api.controller;

import com.ltz.performance.application.service.PerformanceAccountErasureService;
import com.ltz.performance.domain.entity.PerformanceSession;
import com.ltz.performance.domain.enums.SessionStatus;
import com.ltz.performance.infrastructure.client.PerformancePeerDoubles;
import com.ltz.performance.infrastructure.persistence.PostgresIntegrationSupport;
import com.ltz.performance.infrastructure.persistence.repository.PerformanceSessionRepository;
import com.ltz.performance.infrastructure.security.PerformanceTestAuth;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PERF-03/04/13/20: gerçek Auth issuer/JWKS (üretim kaynaklarından derlenmiş), Performance HTTP güvenliği, servisler ve
 * geçici PostgreSQL. Game ve Hardware YEREL SAPMADIR ({@link PerformancePeerDoubles}); bu test onlarla gerçek
 * entegrasyon DEĞİLDİR. Cihaz kaynaklı oturum bu fazda yoktur (yalnız MANUAL).
 */
@SpringBootTest(properties = {"logging.level.root=INFO", "debug=false"})
@AutoConfigureMockMvc
class PerformanceSessionIntegrationTest extends PostgresIntegrationSupport {
    static final String SESSIONS = "/api/v1/performance/sessions";
    static final String FULL_REPORT = "{\"resolution\":\"1920x1080\",\"graphicsPreset\":\"HIGH\",\"avgFps\":92.5,"
            + "\"p1LowFps\":61,\"maxCpuTempC\":78,\"maxGpuTempC\":81,\"avgCpuUsagePercent\":55,"
            + "\"avgGpuUsagePercent\":97,\"notes\":\"Ayarlar elle girildi\"}";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PerformanceSessionRepository repository;
    @Autowired PerformanceAccountErasureService erasure;

    @AfterEach
    void resetPeers() { PerformancePeerDoubles.resetBehaviour(); }

    // ---------- yardımcılar ----------

    static String bearer(UUID account) throws Exception { return "Bearer " + PerformanceTestAuth.AUTH.token(account, "PLAYER"); }

    static UUID game(String title) {
        UUID id = UUID.randomUUID();
        PerformancePeerDoubles.registerGame(id, title);
        return id;
    }

    MockHttpServletRequestBuilder startRequest(UUID account, String body) throws Exception {
        return post(SESSIONS).header("Authorization", bearer(account)).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    String startBody(UUID gameId, UUID profileId, UUID linkId) {
        return "{\"gameId\":\"" + gameId + "\""
                + (profileId == null ? "" : ",\"hardwareProfileId\":\"" + profileId + "\"")
                + (linkId == null ? "" : ",\"deviceLinkId\":\"" + linkId + "\"") + "}";
    }

    UUID startSession(UUID account) throws Exception {
        var result = mvc.perform(startRequest(account, startBody(game("Test Oyunu"), null, null)))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asString());
    }

    int end(UUID account, UUID session, String body) throws Exception {
        var request = post(SESSIONS + "/" + session + "/end").header("Authorization", bearer(account));
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(request).andReturn().getResponse().getStatus();
    }

    UUID completedSession(UUID account) throws Exception {
        UUID session = startSession(account);
        assertThat(end(account, session, null)).isEqualTo(200);
        return session;
    }

    long version(UUID session) {
        return jdbc.queryForObject("SELECT version FROM performance_sessions WHERE id=?", Long.class, session);
    }

    int count(String table, UUID account) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE user_id=?", Integer.class, account);
    }

    // ---------- kimlik doğrulama / yetkilendirme ----------

    @Test
    void anonymousSessionCallsAreRejectedWith401ProblemDetail() throws Exception {
        UUID id = UUID.randomUUID();
        for (var request : List.of(get(SESSIONS), get(SESSIONS + "/active"), get(SESSIONS + "/" + id),
                post(SESSIONS).contentType(MediaType.APPLICATION_JSON).content("{}"), post(SESSIONS + "/" + id + "/end"),
                put(SESSIONS + "/" + id + "/report").contentType(MediaType.APPLICATION_JSON).content("{}"),
                get(SESSIONS + "/" + id + "/report"), delete(SESSIONS + "/" + id),
                delete(SESSIONS + "/" + id + "/report"), delete(SESSIONS + "?confirm=true"))) {
            mvc.perform(request).andExpect(status().isUnauthorized())
                    .andExpect(header().string("WWW-Authenticate", "Bearer"))
                    .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        }
    }

    @Test
    void deviceTokenAndUnknownSubpathsCannotReachSessionRoutes() throws Exception {
        UUID account = UUID.randomUUID();
        String device = "Bearer " + PerformanceTestAuth.performanceDeviceToken(UUID.randomUUID(), account);
        mvc.perform(get(SESSIONS).header("Authorization", device)).andExpect(status().isUnauthorized());
        mvc.perform(get(SESSIONS + "/" + UUID.randomUUID() + "/unknown").header("Authorization", bearer(account)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    // ---------- PERF-03: oturum başlatma ----------

    @Test
    void startCreatesActiveManualSessionWithServerTimeAndPrivateHeaders() throws Exception {
        UUID account = UUID.randomUUID();
        UUID gameId = game("Hades II");
        Instant before = Instant.now().minusSeconds(2);
        mvc.perform(startRequest(account, startBody(gameId, null, null))).andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(jsonPath("$.status").value("ACTIVE")).andExpect(jsonPath("$.source").value("MANUAL"))
                .andExpect(jsonPath("$.gameId").value(gameId.toString()))
                .andExpect(jsonPath("$.gameTitle").value("Hades II")).andExpect(jsonPath("$.endedAt").doesNotExist())
                .andExpect(jsonPath("$.durationSeconds").doesNotExist());
        Instant started = jdbc.queryForObject("SELECT started_at FROM performance_sessions WHERE user_id=?",
                Timestamp.class, account).toInstant();
        assertThat(started).isBetween(before, Instant.now().plusSeconds(2));
        assertThat(PerformancePeerDoubles.hardwareCalls()).isZero();
        mvc.perform(get(SESSIONS + "/active").header("Authorization", bearer(account))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void startRejectsInvalidBodiesAndClientSuppliedTimesOrStatus() throws Exception {
        UUID account = UUID.randomUUID();
        UUID gameId = game("Test");
        for (String body : List.of("{}", "{\"gameId\":\"yok\"}", "{\"gameId\":null}",
                "{\"gameId\":\"" + gameId + "\",\"startedAt\":\"2020-01-01T00:00:00Z\"}",
                "{\"gameId\":\"" + gameId + "\",\"status\":\"COMPLETED\"}",
                "{\"gameId\":\"" + gameId + "\",\"userId\":\"" + UUID.randomUUID() + "\"}", "[]", "")) {
            mvc.perform(startRequest(account, body)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        assertThat(count("performance_sessions", account)).isZero();
    }

    @Test
    void unknownGameIsRejectedWith422AndNothingIsStored() throws Exception {
        UUID account = UUID.randomUUID();
        mvc.perform(startRequest(account, startBody(UUID.randomUUID(), null, null)))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("GAME_NOT_FOUND"));
        assertThat(count("performance_sessions", account)).isZero();
    }

    @Test
    void gameServiceFailuresFailClosedWith503() throws Exception {
        UUID account = UUID.randomUUID();
        UUID gameId = game("Test");
        for (int status : List.of(400, 401, 403, 429, 500, 503)) {
            PerformancePeerDoubles.forceGame(status, null);
            mvc.perform(startRequest(account, startBody(gameId, null, null))).andExpect(status().isServiceUnavailable())
                    .andExpect(header().string("Retry-After", "30"))
                    .andExpect(jsonPath("$.code").value("GAME_SERVICE_UNAVAILABLE"));
        }
        for (String malformed : List.of("not-json", "[]", "{}", "{\"items\":[],\"unavailable\":[]}",
                "{\"items\":[{\"gameId\":\"" + gameId + "\",\"title\":\"  \"}],\"unavailable\":[]}",
                "{\"items\":[{\"gameId\":\"" + gameId + "\"}],\"unavailable\":[]}",
                "{\"items\":[{\"gameId\":\"bozuk-uuid\"}],\"unavailable\":[]}")) {
            PerformancePeerDoubles.forceGame(200, malformed);
            mvc.perform(startRequest(account, startBody(gameId, null, null))).andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("GAME_SERVICE_UNAVAILABLE"));
        }
        assertThat(count("performance_sessions", account)).isZero();
    }

    @Test
    void hardwareProfileOwnershipIsVerifiedWithCallersBearer() throws Exception {
        UUID account = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID profile = UUID.randomUUID();
        UUID foreignProfile = UUID.randomUUID();
        PerformancePeerDoubles.registerProfile(account, profile);
        PerformancePeerDoubles.registerProfile(other, foreignProfile);
        UUID gameId = game("Test");
        String token = bearer(account);
        mvc.perform(post(SESSIONS).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(startBody(gameId, profile, null))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.hardwareProfileId").value(profile.toString()));
        assertThat(PerformancePeerDoubles.lastHardwareAuthorization()).isEqualTo(token);

        UUID next = UUID.randomUUID();
        PerformancePeerDoubles.registerProfile(account, next);
        // başkasının ve olmayan profil aynı şekilde reddedilir (IDOR); aktif oturum çakışmasından önce doğrulanır
        for (UUID unusable : List.of(foreignProfile, UUID.randomUUID())) {
            mvc.perform(startRequest(account, startBody(gameId, unusable, null))).andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("HARDWARE_PROFILE_NOT_FOUND"));
        }
        assertThat(count("performance_sessions", account)).isEqualTo(1);
    }

    @Test
    void hardwareServiceFailuresFailClosedWith503() throws Exception {
        UUID account = UUID.randomUUID();
        UUID profile = UUID.randomUUID();
        PerformancePeerDoubles.registerProfile(account, profile);
        UUID gameId = game("Test");
        for (int status : List.of(400, 401, 403, 413, 500, 503)) {
            PerformancePeerDoubles.forceHardware(status, null, 0);
            mvc.perform(startRequest(account, startBody(gameId, profile, null))).andExpect(status().isServiceUnavailable())
                    .andExpect(header().string("Retry-After", "30"))
                    .andExpect(jsonPath("$.code").value("HARDWARE_SERVICE_UNAVAILABLE"));
        }
        for (String malformed : List.of("not-json", "{}", "{\"profiles\":[]}", "{\"profiles\":[{\"profileId\":\"" + UUID.randomUUID() + "\"}]}",
                "{\"profiles\":[{\"profileId\":1}]}")) {
            PerformancePeerDoubles.forceHardware(200, malformed, 0);
            mvc.perform(startRequest(account, startBody(gameId, profile, null))).andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("HARDWARE_SERVICE_UNAVAILABLE"));
        }
        // okuma zaman aşımı (yerel sapma 1 sn read-timeout'tan uzun bekler)
        PerformancePeerDoubles.forceHardware(200, null, 2500);
        mvc.perform(startRequest(account, startBody(gameId, profile, null))).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("HARDWARE_SERVICE_UNAVAILABLE"));
        assertThat(count("performance_sessions", account)).isZero();
    }

    @Test
    void deviceLinkMustBeOwnedAndActive() throws Exception {
        UUID account = UUID.randomUUID();
        UUID device = UUID.randomUUID();
        PerformanceTestAuth.authDevices(account, device, "Masaüstü");
        mvc.perform(put("/api/v1/performance/sync-consent").header("Authorization", bearer(account))).andExpect(status().isOk());
        var linked = mvc.perform(post("/api/v1/performance/devices").header("Authorization", bearer(account))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"deviceId\":\"" + device + "\"}"))
                .andExpect(status().isOk()).andReturn();
        UUID link = UUID.fromString(json.readTree(linked.getResponse().getContentAsString()).get("id").asString());
        UUID gameId = game("Test");

        mvc.perform(startRequest(UUID.randomUUID(), startBody(gameId, null, link))).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("DEVICE_LINK_NOT_FOUND"));
        mvc.perform(startRequest(account, startBody(gameId, null, UUID.randomUUID()))).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("DEVICE_LINK_NOT_FOUND"));
        mvc.perform(startRequest(account, startBody(gameId, null, link))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.deviceLinkId").value(link.toString())).andExpect(jsonPath("$.source").value("MANUAL"));

        // bağlı oturum varken bağlantı silinemez (RESTRICT); devre dışı bırakmak kaydı korur
        assertThatThrownBy(() -> jdbc.update("DELETE FROM device_links WHERE id=?", link))
                .isInstanceOf(DataIntegrityViolationException.class);
        mvc.perform(delete("/api/v1/performance/devices/" + link).header("Authorization", bearer(account)))
                .andExpect(status().isOk());
        UUID session = jdbc.queryForObject("SELECT id FROM performance_sessions WHERE user_id=?", UUID.class, account);
        assertThat(end(account, session, null)).isEqualTo(200);
        mvc.perform(startRequest(account, startBody(gameId, null, link))).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("DEVICE_LINK_NOT_FOUND"));
    }

    @Test
    void onlyOneActiveSessionPerUserAndNewOneAllowedAfterEnd() throws Exception {
        UUID account = UUID.randomUUID();
        UUID first = startSession(account);
        mvc.perform(startRequest(account, startBody(game("Başka"), null, null))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVE_SESSION_EXISTS"));
        // başka kullanıcı etkilenmez
        startSession(UUID.randomUUID());
        assertThat(end(account, first, null)).isEqualTo(200);
        startSession(account);
        assertThat(count("performance_sessions", account)).isEqualTo(2);
    }

    @Test
    void concurrentStartsCreateExactlyOneActiveSession() throws Exception {
        UUID account = UUID.randomUUID();
        String body = startBody(game("Test"), null, null);
        String token = bearer(account);
        var statuses = runConcurrently(8, _ -> mvc.perform(post(SESSIONS).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus());
        assertThat(statuses.stream().filter(s -> s == 201)).hasSize(1);
        assertThat(statuses.stream().filter(s -> s == 409)).hasSize(7);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM performance_sessions WHERE user_id=? AND status='ACTIVE'",
                Integer.class, account)).isEqualTo(1);
    }

    @Test
    void staleActiveSessionBecomesAbandonedAndDoesNotBlockNewStart() throws Exception {
        UUID account = UUID.randomUUID();
        UUID stale = startSession(account);
        Instant longAgo = Instant.now().minusSeconds(25 * 3600L);
        jdbc.update("UPDATE performance_sessions SET started_at=?, created_at=? WHERE id=?",
                Timestamp.from(longAgo), Timestamp.from(longAgo), stale);
        mvc.perform(get(SESSIONS + "/active").header("Authorization", bearer(account))).andExpect(status().isNotFound());
        startSession(account);
        var row = jdbc.queryForMap("SELECT status, ended_at, started_at FROM performance_sessions WHERE id=?", stale);
        assertThat(row).containsEntry("status", "ABANDONED");
        assertThat(((Timestamp) row.get("ended_at")).toInstant())
                .isEqualTo(((Timestamp) row.get("started_at")).toInstant().plus(PerformanceSession.MAX_ACTIVE));
        // terk edilmiş oturum tekrar bitirilebilir değil: aynı durum idempotent, farklı durum çakışma
        assertThat(end(account, stale, "{\"status\":\"ABANDONED\"}")).isEqualTo(200);
        assertThat(end(account, stale, null)).isEqualTo(409);
    }

    @Test
    void sessionCapIsEnforced() throws Exception {
        UUID account = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO performance_sessions (id, user_id, game_id, game_title, source, status, started_at, ended_at,
                                                  created_at, updated_at)
                SELECT gen_random_uuid(), ?, gen_random_uuid(), 'Oyun', 'MANUAL', 'COMPLETED',
                       now() - interval '2 days', now() - interval '1 day', now(), now()
                FROM generate_series(1, 1000)
                """, account);
        mvc.perform(startRequest(account, startBody(game("Test"), null, null))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_LIMIT_REACHED"));
    }

    @Test
    void sessionWritesAreRateLimited() throws Exception {
        UUID account = UUID.randomUUID();
        UUID gameId = game("Test");
        String body = startBody(gameId, null, null);
        String token = bearer(account);
        int limited = 0;
        for (int i = 0; i < 70; i++) {
            int status = mvc.perform(post(SESSIONS).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                    .content(body)).andReturn().getResponse().getStatus();
            if (status == 429) limited++;
        }
        assertThat(limited).isPositive();
        mvc.perform(post(SESSIONS).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    // ---------- PERF-04: oturumu bitirme ----------

    @Test
    void endCompletesSessionWithConsistentTimesAndIsIdempotent() throws Exception {
        UUID account = UUID.randomUUID();
        UUID session = startSession(account);
        mvc.perform(post(SESSIONS + "/" + session + "/end").header("Authorization", bearer(account)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.endedAt").isNotEmpty()).andExpect(jsonPath("$.durationSeconds").isNumber());
        long bumped = version(session);
        var row = jdbc.queryForMap("SELECT started_at, ended_at FROM performance_sessions WHERE id=?", session);
        assertThat(((Timestamp) row.get("ended_at")).toInstant()).isAfterOrEqualTo(((Timestamp) row.get("started_at")).toInstant());
        // aynı durumu tekrar istemek değişiklik yapmaz
        assertThat(end(account, session, "{\"status\":\"COMPLETED\"}")).isEqualTo(200);
        assertThat(end(account, session, null)).isEqualTo(200);
        assertThat(version(session)).isEqualTo(bumped);
        // farklı bir bitiş durumu mevcut sonucu ezmez
        mvc.perform(post(SESSIONS + "/" + session + "/end").header("Authorization", bearer(account))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ABANDONED\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SESSION_ALREADY_ENDED"));
        assertThat(jdbc.queryForObject("SELECT status FROM performance_sessions WHERE id=?", String.class, session))
                .isEqualTo("COMPLETED");
    }

    @Test
    void endSupportsAbandonedAndRejectsInvalidStatuses() throws Exception {
        UUID account = UUID.randomUUID();
        UUID session = startSession(account);
        for (String body : List.of("{\"status\":\"ACTIVE\"}", "{\"status\":\"BITTI\"}", "{\"startedAt\":\"2020-01-01T00:00:00Z\"}",
                "{\"endedAt\":\"2020-01-01T00:00:00Z\"}")) {
            assertThat(end(account, session, body)).as(body).isEqualTo(400);
        }
        assertThat(jdbc.queryForObject("SELECT status FROM performance_sessions WHERE id=?", String.class, session))
                .isEqualTo("ACTIVE");
        mvc.perform(post(SESSIONS + "/" + session + "/end").header("Authorization", bearer(account))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ABANDONED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ABANDONED"));
        // terk edilen oturuma rapor yazılamaz
        mvc.perform(put(SESSIONS + "/" + session + "/report").header("Authorization", bearer(account))
                        .contentType(MediaType.APPLICATION_JSON).content(FULL_REPORT))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SESSION_NOT_COMPLETED"));
    }

    @Test
    void concurrentEndsWithSameStatusApplyOnce() throws Exception {
        UUID account = UUID.randomUUID();
        UUID session = startSession(account);
        long before = version(session);
        var statuses = runConcurrently(8, _ -> end(account, session, null));
        assertThat(statuses).containsOnly(200);
        assertThat(version(session)).isEqualTo(before + 1);
    }

    @Test
    void concurrentEndsWithDifferentStatusesHaveExactlyOneWinner() throws Exception {
        UUID account = UUID.randomUUID();
        UUID session = startSession(account);
        var statuses = runConcurrently(8, index -> end(account, session,
                index % 2 == 0 ? "{\"status\":\"COMPLETED\"}" : "{\"status\":\"ABANDONED\"}"));
        String finalStatus = jdbc.queryForObject("SELECT status FROM performance_sessions WHERE id=?", String.class, session);
        assertThat(finalStatus).isIn("COMPLETED", "ABANDONED");
        assertThat(statuses).containsOnly(200, 409);
        // kazanan durumu isteyenler 200, diğerleri 409 alır; sonuç ezilmez
        int winners = (int) statuses.stream().filter(s -> s == 200).count();
        assertThat(winners).isEqualTo(4);
        assertThat(version(session)).isEqualTo(1);
    }

    @Test
    void endAndDeleteRaceLeavesConsistentState() throws Exception {
        UUID account = UUID.randomUUID();
        for (int round = 0; round < 5; round++) {
            UUID session = startSession(account);
            var statuses = runConcurrently(2, index -> index == 0 ? end(account, session, null)
                    : mvc.perform(delete(SESSIONS + "/" + session).header("Authorization", bearer(account)))
                            .andReturn().getResponse().getStatus());
            assertThat(statuses.get(1)).isEqualTo(204);
            assertThat(statuses.get(0)).isIn(200, 404);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM performance_sessions WHERE id=?", Integer.class, session))
                    .isZero();
        }
    }

    @Test
    void optimisticLockingRejectsStaleWriter() throws Exception {
        UUID account = UUID.randomUUID();
        UUID id = startSession(account);
        var first = repository.findById(id).orElseThrow();
        var stale = repository.findById(id).orElseThrow();
        first.end(SessionStatus.COMPLETED, Instant.now());
        repository.saveAndFlush(first);
        stale.end(SessionStatus.ABANDONED, Instant.now());
        assertThatThrownBy(() -> repository.saveAndFlush(stale)).isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM performance_sessions WHERE id=?", String.class, id))
                .isEqualTo("COMPLETED");
    }

    // ---------- okuma / listeleme ----------

    @Test
    void listIsPagedNewestFirstAndOwnerOnly() throws Exception {
        UUID account = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        for (int i = 0; i < 3; i++) {
            UUID s = startSession(account);
            end(account, s, null);
        }
        startSession(other);
        mvc.perform(get(SESSIONS).param("page", "0").param("size", "2").header("Authorization", bearer(account)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(jsonPath("$.items.length()").value(2)).andExpect(jsonPath("$.hasNext").value(true));
        mvc.perform(get(SESSIONS).param("page", "1").param("size", "2").header("Authorization", bearer(account)))
                .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.hasNext").value(false));
        var all = json.readTree(mvc.perform(get(SESSIONS).header("Authorization", bearer(account))).andReturn()
                .getResponse().getContentAsString()).get("items");
        List<String> starts = new ArrayList<>();
        all.forEach(item -> starts.add(item.get("startedAt").asString()));
        assertThat(starts).isSortedAccordingTo(java.util.Comparator.reverseOrder());
        for (String bad : List.of("-1", "abc")) {
            mvc.perform(get(SESSIONS).param("page", bad).header("Authorization", bearer(account))).andExpect(status().isBadRequest());
        }
        for (String size : List.of("0", "51", "-5")) {
            mvc.perform(get(SESSIONS).param("size", size).header("Authorization", bearer(account)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
    }

    // ---------- IDOR ----------

    @Test
    void anotherUsersSessionAndReportAreInvisibleAndImmutable() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID intruder = UUID.randomUUID();
        UUID session = completedSession(owner);
        mvc.perform(put(SESSIONS + "/" + session + "/report").header("Authorization", bearer(owner))
                .contentType(MediaType.APPLICATION_JSON).content(FULL_REPORT)).andExpect(status().isCreated());
        String foreign = bearer(intruder);
        String missing = UUID.randomUUID().toString();
        for (String id : List.of(session.toString(), missing)) {
            mvc.perform(get(SESSIONS + "/" + id).header("Authorization", foreign)).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PERFORMANCE_NOT_FOUND"));
            mvc.perform(post(SESSIONS + "/" + id + "/end").header("Authorization", foreign)).andExpect(status().isNotFound());
            mvc.perform(get(SESSIONS + "/" + id + "/report").header("Authorization", foreign)).andExpect(status().isNotFound());
            mvc.perform(put(SESSIONS + "/" + id + "/report").header("Authorization", foreign)
                    .contentType(MediaType.APPLICATION_JSON).content(FULL_REPORT)).andExpect(status().isNotFound());
            mvc.perform(delete(SESSIONS + "/" + id + "/report").header("Authorization", foreign)).andExpect(status().isNotFound());
            mvc.perform(delete(SESSIONS + "/" + id).header("Authorization", foreign)).andExpect(status().isNotFound());
        }
        assertThat(count("performance_sessions", owner)).isEqualTo(1);
        assertThat(count("performance_reports", owner)).isEqualTo(1);
        mvc.perform(delete(SESSIONS).param("confirm", "true").header("Authorization", foreign)).andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedSessions").value(0));
        assertThat(count("performance_sessions", owner)).isEqualTo(1);
    }

    // ---------- PERF-13: rapor ----------

    @Test
    void reportLifecycleIsManualIdempotentAndNeverInventsValues() throws Exception {
        UUID account = UUID.randomUUID();
        UUID session = startSession(account);
        String token = bearer(account);
        // aktif oturuma rapor yazılamaz
        mvc.perform(put(SESSIONS + "/" + session + "/report").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(FULL_REPORT))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SESSION_NOT_COMPLETED"));
        mvc.perform(get(SESSIONS + "/" + session + "/report").header("Authorization", token)).andExpect(status().isNotFound());
        end(account, session, null);

        String minimal = "{\"resolution\":\"2560x1440\",\"graphicsPreset\":\"CUSTOM\",\"avgFps\":144}";
        mvc.perform(put(SESSIONS + "/" + session + "/report").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(minimal))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(jsonPath("$.source").value("MANUAL")).andExpect(jsonPath("$.avgFps").value(144.0))
                .andExpect(jsonPath("$.p1LowFps").doesNotExist()).andExpect(jsonPath("$.maxGpuTempC").doesNotExist())
                .andExpect(jsonPath("$.avgCpuUsagePercent").doesNotExist()).andExpect(jsonPath("$.notes").doesNotExist());
        // aynı değerlerle tekrar: 200, kayıt değişmez
        long reportVersion = jdbc.queryForObject("SELECT version FROM performance_reports WHERE session_id=?", Long.class, session);
        mvc.perform(put(SESSIONS + "/" + session + "/report").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(minimal)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT version FROM performance_reports WHERE session_id=?", Long.class, session))
                .isEqualTo(reportVersion);
        // değişen değerlerle güncelleme: 200, tek satır
        mvc.perform(put(SESSIONS + "/" + session + "/report").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(FULL_REPORT))
                .andExpect(status().isOk()).andExpect(jsonPath("$.graphicsPreset").value("HIGH"))
                .andExpect(jsonPath("$.resolution").value("1920x1080")).andExpect(jsonPath("$.p1LowFps").value(61.0));
        assertThat(count("performance_reports", account)).isEqualTo(1);
        mvc.perform(get(SESSIONS + "/" + session + "/report").header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(session.toString()));
    }

    @Test
    void reportValidationRejectsInvalidAndEmptyInput() throws Exception {
        UUID account = UUID.randomUUID();
        UUID session = completedSession(account);
        String base = "\"resolution\":\"1920x1080\",\"graphicsPreset\":\"HIGH\"";
        for (String body : List.of("{\"graphicsPreset\":\"HIGH\",\"avgFps\":60}", "{\"resolution\":\"1920x1080\",\"avgFps\":60}",
                "{\"resolution\":\"abc\",\"graphicsPreset\":\"HIGH\",\"avgFps\":60}",
                "{\"resolution\":\"1920X1080\",\"graphicsPreset\":\"HIGH\",\"avgFps\":60}",
                "{\"resolution\":\"1920x1080\",\"graphicsPreset\":\"EPIC\",\"avgFps\":60}",
                "{" + base + ",\"avgFps\":0}", "{" + base + ",\"avgFps\":-1}", "{" + base + ",\"avgFps\":1001}",
                "{" + base + ",\"p1LowFps\":0}", "{" + base + ",\"maxCpuTempC\":-1}", "{" + base + ",\"maxGpuTempC\":151}",
                "{" + base + ",\"avgCpuUsagePercent\":100.5}", "{" + base + ",\"avgGpuUsagePercent\":-0.1}",
                "{" + base + ",\"avgFps\":\"hızlı\"}", "{" + base + ",\"avgFps\":60,\"source\":\"DEVICE\"}",
                "{" + base + ",\"avgFps\":60,\"sessionId\":\"" + UUID.randomUUID() + "\"}",
                "{" + base + ",\"avgFps\":60,\"notes\":\"" + "x".repeat(501) + "\"}", "{}", "[]")) {
            mvc.perform(put(SESSIONS + "/" + session + "/report").header("Authorization", bearer(account))
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        assertThat(count("performance_reports", account)).isZero();
    }

    @Test
    void reportWithAllMetricsNullOrInconsistentFpsIsRejectedWith422() throws Exception {
        UUID account = UUID.randomUUID();
        UUID session = completedSession(account);
        String base = "{\"resolution\":\"1920x1080\",\"graphicsPreset\":\"LOW\"";
        // ölçüm yoksa değer uydurulmaz: hiç ölçüm de, yalnız not da reddedilir
        for (String body : List.of(base + "}", base + ",\"notes\":\"yalnız not\"}")) {
            mvc.perform(put(SESSIONS + "/" + session + "/report").header("Authorization", bearer(account))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("REPORT_EMPTY"));
        }
        mvc.perform(put(SESSIONS + "/" + session + "/report").header("Authorization", bearer(account))
                        .contentType(MediaType.APPLICATION_JSON).content(base + ",\"avgFps\":50,\"p1LowFps\":70}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("REPORT_INCONSISTENT"));
        assertThat(count("performance_reports", account)).isZero();
    }

    @Test
    void reportNotesAreSanitized() throws Exception {
        UUID account = UUID.randomUUID();
        UUID session = completedSession(account);
        mvc.perform(put(SESSIONS + "/" + session + "/report").header("Authorization", bearer(account))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resolution\":\"1920x1080\",\"graphicsPreset\":\"LOW\",\"avgFps\":50,\"notes\":\"  a\\u0000b\\nc  \"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.notes").value("a b c"));
    }

    // ---------- PERF-20: silme ----------

    @Test
    void deleteReportKeepsSessionAndRepeatIsNotFound() throws Exception {
        UUID account = UUID.randomUUID();
        UUID session = completedSession(account);
        String token = bearer(account);
        mvc.perform(put(SESSIONS + "/" + session + "/report").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(FULL_REPORT)).andExpect(status().isCreated());
        mvc.perform(delete(SESSIONS + "/" + session + "/report").header("Authorization", token)).andExpect(status().isNoContent());
        mvc.perform(delete(SESSIONS + "/" + session + "/report").header("Authorization", token)).andExpect(status().isNotFound());
        mvc.perform(get(SESSIONS + "/" + session).header("Authorization", token)).andExpect(status().isOk());
        // rapor silindikten sonra yeniden yazılabilir
        mvc.perform(put(SESSIONS + "/" + session + "/report").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(FULL_REPORT)).andExpect(status().isCreated());
    }

    @Test
    void deleteSessionCascadesReportAndRepeatIsNotFound() throws Exception {
        UUID account = UUID.randomUUID();
        UUID session = completedSession(account);
        String token = bearer(account);
        mvc.perform(put(SESSIONS + "/" + session + "/report").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(FULL_REPORT)).andExpect(status().isCreated());
        mvc.perform(delete(SESSIONS + "/" + session).header("Authorization", token)).andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store, private"));
        assertThat(count("performance_sessions", account)).isZero();
        assertThat(count("performance_reports", account)).isZero();
        mvc.perform(delete(SESSIONS + "/" + session).header("Authorization", token)).andExpect(status().isNotFound());
        // aktif oturum da silinebilir ve yeni oturum açmayı engellemez
        UUID active = startSession(account);
        mvc.perform(delete(SESSIONS + "/" + active).header("Authorization", token)).andExpect(status().isNoContent());
        startSession(account);
    }

    @Test
    void deleteAllRequiresExplicitConfirmationAndOnlyAffectsCaller() throws Exception {
        UUID account = UUID.randomUUID();
        UUID bystander = UUID.randomUUID();
        UUID session = completedSession(account);
        mvc.perform(put(SESSIONS + "/" + session + "/report").header("Authorization", bearer(account))
                .contentType(MediaType.APPLICATION_JSON).content(FULL_REPORT)).andExpect(status().isCreated());
        completedSession(account);
        completedSession(bystander);
        String token = bearer(account);
        mvc.perform(delete(SESSIONS).header("Authorization", token)).andExpect(status().isBadRequest());
        mvc.perform(delete(SESSIONS).param("confirm", "false").header("Authorization", token)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertThat(count("performance_sessions", account)).isEqualTo(2);
        mvc.perform(delete(SESSIONS).param("confirm", "true").header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedSessions").value(2));
        mvc.perform(delete(SESSIONS).param("confirm", "true").header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedSessions").value(0));
        assertThat(count("performance_sessions", account)).isZero();
        assertThat(count("performance_reports", account)).isZero();
        assertThat(count("performance_sessions", bystander)).isEqualTo(1);
    }

    @Test
    void accountErasureRemovesSessionsAndReportsOfTargetOnly() throws Exception {
        UUID target = UUID.randomUUID();
        UUID bystander = UUID.randomUUID();
        for (UUID account : List.of(target, bystander)) {
            UUID session = completedSession(account);
            mvc.perform(put(SESSIONS + "/" + session + "/report").header("Authorization", bearer(account))
                    .contentType(MediaType.APPLICATION_JSON).content(FULL_REPORT)).andExpect(status().isCreated());
            startSession(account);
        }
        assertThat(erasure.erase(target)).isTrue();
        assertThat(count("performance_sessions", target)).isZero();
        assertThat(count("performance_reports", target)).isZero();
        assertThat(count("performance_sessions", bystander)).isEqualTo(2);
        assertThat(count("performance_reports", bystander)).isEqualTo(1);
        // silinen hesap yeni oturum açamaz (silme çiti)
        mvc.perform(startRequest(target, startBody(game("Test"), null, null))).andExpect(status().is4xxClientError());
    }

    // ---------- veritabanı kısıtları (gerçek PostgreSQL) ----------

    private UUID insertSession(UUID account, String status, String startedAt, String endedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO performance_sessions (id, user_id, game_id, game_title, source, status, started_at, ended_at,
                                                  created_at, updated_at)
                VALUES (?, ?, gen_random_uuid(), 'Oyun', 'MANUAL', ?, ?::timestamptz, ?::timestamptz, now(), now())
                """, id, account, status, startedAt, endedAt);
        return id;
    }

    @Test
    void sessionConstraintsAreEnforcedByDatabase() {
        UUID account = UUID.randomUUID();
        insertSession(account, "ACTIVE", "2026-01-01T10:00:00Z", null);
        assertThatThrownBy(() -> insertSession(account, "ACTIVE", "2026-01-01T11:00:00Z", null))
                .as("kullanıcı başına tek ACTIVE").isInstanceOf(DataIntegrityViolationException.class);
        UUID other = UUID.randomUUID();
        assertThatThrownBy(() -> insertSession(other, "ACTIVE", "2026-01-01T10:00:00Z", "2026-01-01T11:00:00Z"))
                .as("ACTIVE bitişsiz olmalı").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertSession(other, "COMPLETED", "2026-01-01T10:00:00Z", null))
                .as("COMPLETED bitişli olmalı").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertSession(other, "COMPLETED", "2026-01-01T10:00:00Z", "2026-01-01T09:00:00Z"))
                .as("bitiş başlangıçtan önce olamaz").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertSession(other, "BITTI", "2026-01-01T10:00:00Z", "2026-01-01T11:00:00Z"))
                .as("durum kümesi").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO performance_sessions (id, user_id, game_id, game_title, source, status, started_at, created_at, updated_at)
                VALUES (gen_random_uuid(), ?, gen_random_uuid(), 'Oyun', 'DEVICE', 'ACTIVE', now(), now(), now())
                """, other)).as("kaynak yalnız MANUAL").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO performance_sessions (id, user_id, game_id, game_title, source, status, started_at, created_at, updated_at)
                VALUES (gen_random_uuid(), ?, gen_random_uuid(), '   ', 'MANUAL', 'ACTIVE', now(), now(), now())
                """, other)).as("oyun adı boş olamaz").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO performance_sessions (id, user_id, device_link_id, game_id, game_title, source, status, started_at,
                                                  created_at, updated_at)
                VALUES (gen_random_uuid(), ?, gen_random_uuid(), gen_random_uuid(), 'Oyun', 'MANUAL', 'ACTIVE', now(), now(), now())
                """, other)).as("olmayan cihaz bağlantısı").isInstanceOf(DataIntegrityViolationException.class);
        // tamamlanmış oturumlar birden fazla olabilir
        insertSession(account, "COMPLETED", "2026-01-01T08:00:00Z", "2026-01-01T09:00:00Z");
        insertSession(account, "ABANDONED", "2026-01-01T06:00:00Z", "2026-01-01T07:00:00Z");
    }

    @Test
    void reportConstraintsAreEnforcedByDatabase() {
        UUID account = UUID.randomUUID();
        UUID session = insertSession(account, "COMPLETED", "2026-01-01T08:00:00Z", "2026-01-01T09:00:00Z");
        String ok = "1920x1080";
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO performance_reports (id, session_id, user_id, resolution, graphics_preset, source, created_at, updated_at)
                VALUES (gen_random_uuid(), ?, ?, ?, 'HIGH', 'MANUAL', now(), now())""", session, account, ok))
                .as("en az bir ölçüm").isInstanceOf(DataIntegrityViolationException.class);
        for (String[] bad : new String[][]{{"abc", "HIGH", "60"}, {ok, "EPIC", "60"}, {ok, "HIGH", "0"}, {ok, "HIGH", "1001"}}) {
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO performance_reports (id, session_id, user_id, resolution, graphics_preset, avg_fps, source, created_at, updated_at)
                    VALUES (gen_random_uuid(), ?, ?, ?, ?, ?::double precision, 'MANUAL', now(), now())""",
                    session, account, bad[0], bad[1], bad[2])).as(String.join("/", bad))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        for (String column : List.of("max_cpu_temp_c=-1", "max_gpu_temp_c=151", "avg_cpu_usage_percent=101",
                "avg_gpu_usage_percent=-1", "p1_low_fps=0")) {
            String name = column.substring(0, column.indexOf('='));
            String value = column.substring(column.indexOf('=') + 1);
            assertThatThrownBy(() -> jdbc.update("INSERT INTO performance_reports (id, session_id, user_id, resolution, "
                    + "graphics_preset, " + name + ", source, created_at, updated_at) VALUES (gen_random_uuid(), ?, ?, ?, "
                    + "'HIGH', " + value + ", 'MANUAL', now(), now())", session, account, ok)).as(column)
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO performance_reports (id, session_id, user_id, resolution, graphics_preset, avg_fps, p1_low_fps, source, created_at, updated_at)
                VALUES (gen_random_uuid(), ?, ?, ?, 'HIGH', 50, 60, 'MANUAL', now(), now())""", session, account, ok))
                .as("p1 low ortalamadan büyük olamaz").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO performance_reports (id, session_id, user_id, resolution, graphics_preset, avg_fps, source, created_at, updated_at)
                VALUES (gen_random_uuid(), ?, ?, ?, 'HIGH', 60, 'DEVICE', now(), now())""", session, account, ok))
                .as("kaynak yalnız MANUAL").isInstanceOf(DataIntegrityViolationException.class);
        String tooLongNotes = "x".repeat(501);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO performance_reports (id, session_id, user_id, resolution, graphics_preset, avg_fps, notes, source, created_at, updated_at)
                VALUES (gen_random_uuid(), ?, ?, ?, 'HIGH', 60, ?, 'MANUAL', now(), now())""", session, account, ok, tooLongNotes))
                .as("not uzunluğu").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO performance_reports (id, session_id, user_id, resolution, graphics_preset, avg_fps, source, created_at, updated_at)
                VALUES (gen_random_uuid(), gen_random_uuid(), ?, ?, 'HIGH', 60, 'MANUAL', now(), now())""", account, ok))
                .as("olmayan oturum").isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update("""
                INSERT INTO performance_reports (id, session_id, user_id, resolution, graphics_preset, avg_fps, source, created_at, updated_at)
                VALUES (gen_random_uuid(), ?, ?, ?, 'HIGH', 60, 'MANUAL', now(), now())""", session, account, ok);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO performance_reports (id, session_id, user_id, resolution, graphics_preset, avg_fps, source, created_at, updated_at)
                VALUES (gen_random_uuid(), ?, ?, ?, 'LOW', 30, 'MANUAL', now(), now())""", session, account, ok))
                .as("oturum başına tek rapor").isInstanceOf(DataIntegrityViolationException.class);
        // oturum silinince rapor da gider (CASCADE)
        jdbc.update("DELETE FROM performance_sessions WHERE id=?", session);
        assertThat(count("performance_reports", account)).isZero();
    }

    // ---------- eşzamanlılık yardımcısı ----------

    /** {@code count} iş parçacığını aynı anda başlatır; sonuçlar iş parçacığı sırasıyla döner. */
    private static List<Integer> runConcurrently(int count, ThrowingIntFunction task) throws Exception {
        try (var pool = Executors.newFixedThreadPool(count)) {
            var ready = new CountDownLatch(count);
            var go = new CountDownLatch(1);
            var futures = new ArrayList<java.util.concurrent.Future<Integer>>();
            for (int i = 0; i < count; i++) {
                final int index = i;
                Callable<Integer> call = () -> {
                    ready.countDown();
                    go.await();
                    return task.apply(index);
                };
                futures.add(pool.submit(call));
            }
            ready.await();
            go.countDown();
            var results = new ArrayList<Integer>();
            for (var future : futures) results.add(future.get());
            return results;
        }
    }

    @FunctionalInterface
    private interface ThrowingIntFunction {
        int apply(int index) throws Exception;
    }
}

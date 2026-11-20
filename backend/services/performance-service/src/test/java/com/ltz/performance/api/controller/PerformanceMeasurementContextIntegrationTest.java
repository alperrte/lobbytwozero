package com.ltz.performance.api.controller;

import com.ltz.performance.infrastructure.client.PerformancePeerDoubles;
import com.ltz.performance.infrastructure.persistence.PostgresIntegrationSupport;
import com.ltz.performance.infrastructure.security.PerformanceTestAuth;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * REC-09 / PERF-12/13/21: ölçüm bağlamının oturuma sabitlenmesi ve v3 senkron zarfı. Gerçek Auth issuer/JWKS,
 * geçici PostgreSQL; Game/Hardware YEREL SAPMADIR ({@link PerformancePeerDoubles}) ve gerçek entegrasyon değildir.
 */
@SpringBootTest(properties = {"logging.level.root=INFO", "debug=false"})
@AutoConfigureMockMvc
class PerformanceMeasurementContextIntegrationTest extends PostgresIntegrationSupport {
    static final String SESSIONS = "/api/v1/performance/sessions";
    static final String CONSENT = "/api/v1/performance/sync-consent";
    static final String DEVICES = "/api/v1/performance/devices";
    static final String BATCHES = "/api/v1/performance/sync/batches";
    static final String CONTEXT = "{\"hardwareProfileVersion\":3,\"resolution\":\"1920x1080\",\"graphicsPreset\":\"HIGH\","
            + "\"renderingMode\":\"NATIVE_RASTER\"}";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    @AfterEach
    void resetPeers() { PerformancePeerDoubles.resetBehaviour(); }

    record Rig(UUID account, UUID device, UUID link, UUID game, UUID profile) { }

    static String user(UUID account) throws Exception { return "Bearer " + PerformanceTestAuth.AUTH.token(account, "PLAYER"); }

    JsonNode send(MockHttpServletRequestBuilder request, int expected) throws Exception {
        var response = mvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(expected);
        var body = response.getContentAsString();
        return body.isBlank() ? null : json.readTree(body);
    }

    UUID link(UUID account, UUID device) throws Exception {
        PerformanceTestAuth.authDevices(account, device, "Masaüstü PC");
        var linked = send(post(DEVICES).header("Authorization", user(account)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"deviceId\":\"" + device + "\"}"), 200);
        return UUID.fromString(linked.get("id").asString());
    }

    Rig rig() throws Exception {
        UUID account = UUID.randomUUID();
        UUID device = UUID.randomUUID();
        send(put(CONSENT).header("Authorization", user(account)), 200);
        UUID link = link(account, device);
        UUID game = UUID.randomUUID();
        UUID profile = UUID.randomUUID();
        PerformancePeerDoubles.registerGame(game, "Test Oyunu");
        PerformancePeerDoubles.registerProfile(account, profile, 3);
        return new Rig(account, device, link, game, profile);
    }

    String body(Rig rig, String context) {
        return "{\"gameId\":\"" + rig.game() + "\",\"hardwareProfileId\":\"" + rig.profile() + "\",\"deviceLinkId\":\""
                + rig.link() + "\"" + (context == null ? "" : ",\"measurementContext\":" + context) + "}";
    }

    MockHttpServletRequestBuilder start(Rig rig, String body) throws Exception {
        return post(SESSIONS).header("Authorization", user(rig.account())).contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    UUID startBound(Rig rig) throws Exception {
        return UUID.fromString(send(start(rig, body(rig, CONTEXT)), 201).get("id").asString());
    }

    MockHttpServletRequestBuilder push(UUID account, UUID device, UUID key, String body) throws Exception {
        return post(BATCHES).header("Authorization", "Bearer " + PerformanceTestAuth.performanceDeviceToken(device, account))
                .header("Idempotency-Key", key.toString()).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    static String sample(Instant at, String value) {
        return "{\"capturedAt\":\"" + at + "\",\"metric\":\"FPS\",\"value\":" + value + "}";
    }

    static String envelope(int schema, UUID session, String context, String... samples) {
        return "{\"schemaVersion\":" + schema + ",\"clientSentAt\":\"" + Instant.now() + "\",\"sessionId\":\"" + session
                + "\"" + (context == null ? "" : ",\"measurementContext\":" + context) + ",\"samples\":["
                + String.join(",", samples) + "]}";
    }

    static Instant base() { return Instant.now().truncatedTo(ChronoUnit.MILLIS).minusSeconds(30); }

    int contextRows(UUID session) {
        return jdbc.queryForObject("SELECT count(*) FROM performance_samples WHERE session_id=? "
                + "AND measurement_context_id=?", Integer.class, session, session);
    }

    int rows(UUID session) {
        return jdbc.queryForObject("SELECT count(*) FROM performance_samples WHERE session_id=?", Integer.class, session);
    }

    // ---------- oturum başlatma ----------

    @Test
    void startBindsOwnerVerifiedContextAndReturnsIt() throws Exception {
        var rig = rig();
        var created = send(start(rig, body(rig, CONTEXT)), 201);
        assertThat(created.at("/measurementContext/hardwareProfileVersion").asLong()).isEqualTo(3);
        assertThat(created.at("/measurementContext/resolution").asString()).isEqualTo("1920x1080");
        assertThat(created.at("/measurementContext/graphicsPreset").asString()).isEqualTo("HIGH");
        assertThat(created.at("/measurementContext/renderingMode").asString()).isEqualTo("NATIVE_RASTER");
        UUID id = UUID.fromString(created.get("id").asString());
        var row = jdbc.queryForMap("SELECT measurement_context_id, hardware_profile_version, measurement_resolution, "
                + "measurement_graphics_preset, measurement_rendering_mode FROM performance_sessions WHERE id=?", id);
        assertThat(row).containsEntry("measurement_context_id", id).containsEntry("hardware_profile_version", 3L)
                .containsEntry("measurement_resolution", "1920x1080").containsEntry("measurement_graphics_preset", "HIGH")
                .containsEntry("measurement_rendering_mode", "NATIVE_RASTER");
    }

    @Test
    void startWithoutContextStaysBackwardCompatible() throws Exception {
        var rig = rig();
        var created = send(start(rig, body(rig, null)), 201);
        assertThat(created.get("measurementContext").isNull()).isTrue();
        UUID id = UUID.fromString(created.get("id").asString());
        assertThat(jdbc.queryForObject("SELECT measurement_context_id FROM performance_sessions WHERE id=?",
                UUID.class, id)).isNull();
    }

    @Test
    void staleProfileVersionIsAConflictAndNoSessionIsCreated() throws Exception {
        var rig = rig();
        PerformancePeerDoubles.registerProfile(rig.account(), rig.profile(), 4);
        var problem = send(start(rig, body(rig, CONTEXT)), 409);
        assertThat(problem.get("code").asString()).isEqualTo("MEASUREMENT_PROFILE_VERSION_CHANGED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM performance_sessions WHERE user_id=?", Integer.class,
                rig.account())).isZero();
    }

    @Test
    void foreignProfileDeviceRequirementAndInvalidValuesAreRejected() throws Exception {
        var rig = rig();
        String foreign = "{\"gameId\":\"" + rig.game() + "\",\"hardwareProfileId\":\"" + UUID.randomUUID()
                + "\",\"deviceLinkId\":\"" + rig.link() + "\",\"measurementContext\":" + CONTEXT + "}";
        assertThat(send(start(rig, foreign), 422).get("code").asString()).isEqualTo("HARDWARE_PROFILE_NOT_FOUND");
        String noDevice = "{\"gameId\":\"" + rig.game() + "\",\"hardwareProfileId\":\"" + rig.profile()
                + "\",\"measurementContext\":" + CONTEXT + "}";
        assertThat(send(start(rig, noDevice), 400).get("code").asString()).isEqualTo("INVALID_REQUEST");
        String noProfile = "{\"gameId\":\"" + rig.game() + "\",\"deviceLinkId\":\"" + rig.link()
                + "\",\"measurementContext\":" + CONTEXT + "}";
        assertThat(send(start(rig, noProfile), 400).get("code").asString()).isEqualTo("INVALID_REQUEST");
        for (String bad : List.of(
                CONTEXT.replace("1920x1080", "1920"), CONTEXT.replace("HIGH", "EPIC"),
                CONTEXT.replace("NATIVE_RASTER", "FRAME_GENERATION"), CONTEXT.replace("\"hardwareProfileVersion\":3,", ""),
                CONTEXT.replace("3,", "-1,"), CONTEXT.replace("1920x1080", "20000x1080"),
                CONTEXT.replace("}", ",\"extra\":1}"))) {
            assertThat(send(start(rig, body(rig, bad)), 400).get("code").asString()).as(bad).isEqualTo("INVALID_REQUEST");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM performance_sessions WHERE user_id=?", Integer.class,
                rig.account())).isZero();
    }

    @Test
    void hardwareOutageFailsClosedWithoutASession() throws Exception {
        var rig = rig();
        PerformancePeerDoubles.forceHardware(503, null, 0);
        assertThat(send(start(rig, body(rig, CONTEXT)), 503).get("code").asString())
                .isEqualTo("HARDWARE_SERVICE_UNAVAILABLE");
        PerformancePeerDoubles.forceHardware(200, "{\"profiles\":[{\"profileId\":\"" + rig.profile() + "\"}]}", 0);
        send(start(rig, body(rig, CONTEXT)), 503);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM performance_sessions WHERE user_id=?", Integer.class,
                rig.account())).isZero();
    }

    // ---------- v3 senkron ----------

    @Test
    void v3BatchMarksSamplesWithTheSessionContext() throws Exception {
        var rig = rig();
        UUID session = startBound(rig);
        Instant at = base();
        var result = send(push(rig.account(), rig.device(), UUID.randomUUID(),
                envelope(3, session, CONTEXT, sample(at, "60"), sample(at.plusSeconds(1), "61"))), 200);
        assertThat(result.get("storedSamples").asInt()).isEqualTo(2);
        assertThat(contextRows(session)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT schema_version FROM sync_batches WHERE id=?", Integer.class,
                UUID.fromString(result.get("batchId").asString()))).isEqualTo(3);
    }

    @Test
    void v3ContextMismatchIsAConflictAndStoresNothing() throws Exception {
        var rig = rig();
        UUID session = startBound(rig);
        for (String other : List.of(CONTEXT.replace("HIGH", "LOW"), CONTEXT.replace("1920x1080", "2560x1440"),
                CONTEXT.replace("\"hardwareProfileVersion\":3", "\"hardwareProfileVersion\":2"))) {
            var problem = send(push(rig.account(), rig.device(), UUID.randomUUID(),
                    envelope(3, session, other, sample(base(), "60"))), 409);
            assertThat(problem.get("code").asString()).isEqualTo("MEASUREMENT_CONTEXT_MISMATCH");
        }
        assertThat(rows(session)).isZero();
    }

    @Test
    void v3AgainstASessionWithoutContextIsAConflict() throws Exception {
        var rig = rig();
        UUID session = UUID.fromString(send(start(rig, body(rig, null)), 201).get("id").asString());
        var problem = send(push(rig.account(), rig.device(), UUID.randomUUID(),
                envelope(3, session, CONTEXT, sample(base(), "60"))), 409);
        assertThat(problem.get("code").asString()).isEqualTo("MEASUREMENT_CONTEXT_MISMATCH");
        assertThat(rows(session)).isZero();
    }

    @Test
    void v3FromAnotherLinkedDeviceOfTheSameAccountIsForbidden() throws Exception {
        var rig = rig();
        UUID session = startBound(rig);
        UUID otherDevice = UUID.randomUUID();
        link(rig.account(), otherDevice);
        var problem = send(push(rig.account(), otherDevice, UUID.randomUUID(),
                envelope(3, session, CONTEXT, sample(base(), "60"))), 403);
        assertThat(problem.get("code").asString()).isEqualTo("MEASUREMENT_DEVICE_MISMATCH");
        assertThat(rows(session)).isZero();
    }

    @Test
    void v3AgainstAnotherAccountsSessionIsNotFound() throws Exception {
        var rig = rig();
        var other = rig();
        UUID session = startBound(rig);
        send(push(other.account(), other.device(), UUID.randomUUID(), envelope(3, session, CONTEXT, sample(base(), "60"))), 404);
        assertThat(rows(session)).isZero();
    }

    @Test
    void shapeRulesRequireContextOnlyForV3() throws Exception {
        var rig = rig();
        UUID session = startBound(rig);
        String at = sample(base(), "60");
        for (String bad : List.of(envelope(3, session, null, at), envelope(2, session, CONTEXT, at),
                "{\"schemaVersion\":1,\"clientSentAt\":\"" + Instant.now() + "\",\"sampleCount\":1,\"measurementContext\":"
                        + CONTEXT + "}",
                envelope(4, session, CONTEXT, at), envelope(3, session, CONTEXT.replace("NATIVE_RASTER", "RT"), at))) {
            assertThat(send(push(rig.account(), rig.device(), UUID.randomUUID(), bad), 400).get("code").asString())
                    .as(bad).isEqualTo("INVALID_REQUEST");
        }
        assertThat(rows(session)).isZero();
    }

    @Test
    void v1AndV2StayBackwardCompatibleAndNeverBecomeEvidence() throws Exception {
        var rig = rig();
        UUID session = startBound(rig);
        send(push(rig.account(), rig.device(), UUID.randomUUID(), "{\"schemaVersion\":1,\"clientSentAt\":\""
                + Instant.now() + "\",\"sampleCount\":4}"), 200);
        var result = send(push(rig.account(), rig.device(), UUID.randomUUID(),
                envelope(2, session, null, sample(base(), "60"))), 200);
        assertThat(result.get("storedSamples").asInt()).isEqualTo(1);
        assertThat(rows(session)).isEqualTo(1);
        assertThat(contextRows(session)).isZero();
    }

    @Test
    void duplicateSamplesDoNotUpgradeEarlierUnboundRowsIntoEvidence() throws Exception {
        var rig = rig();
        UUID session = startBound(rig);
        Instant at = base();
        send(push(rig.account(), rig.device(), UUID.randomUUID(), envelope(2, session, null, sample(at, "60"))), 200);
        var result = send(push(rig.account(), rig.device(), UUID.randomUUID(),
                envelope(3, session, CONTEXT, sample(at, "60"), sample(at.plusSeconds(1), "61"))), 200);
        assertThat(result.get("duplicateSamples").asInt()).isEqualTo(1);
        assertThat(result.get("storedSamples").asInt()).isEqualTo(1);
        assertThat(rows(session)).isEqualTo(2);
        assertThat(contextRows(session)).isEqualTo(1);
    }

    @Test
    void idempotencyHashCoversTheContext() throws Exception {
        var rig = rig();
        UUID session = startBound(rig);
        UUID key = UUID.randomUUID();
        String content = envelope(3, session, CONTEXT, sample(base(), "60"));
        var first = send(push(rig.account(), rig.device(), key, content), 200);
        var replay = send(push(rig.account(), rig.device(), key, content), 200);
        assertThat(replay.get("replayed").asBoolean()).isTrue();
        assertThat(replay.get("batchId").asString()).isEqualTo(first.get("batchId").asString());
        // Aynı anahtar ve örnek, farklı bağlam: yeniden kullanılmış anahtar sayılır, sessizce kabul edilmez.
        var problem = send(push(rig.account(), rig.device(), key,
                envelope(3, session, CONTEXT.replace("HIGH", "LOW"), sample(base(), "60"))), 409);
        assertThat(problem.get("code").asString()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
    }
}

package com.ltz.performance.api.controller;

import com.ltz.performance.infrastructure.client.PerformancePeerDoubles;
import com.ltz.performance.infrastructure.persistence.PostgresIntegrationSupport;
import com.ltz.performance.infrastructure.security.PerformanceTestAuth;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * REC-09 / PERF-12/13/21: bağlamlı cihaz ölçümünden FPS kanıtı. Gerçek Auth issuer/JWKS ve geçici PostgreSQL;
 * oturum/örnekler JDBC ile tohumlanır (ölçüm içeriği iş kuralı sınırlarını deterministik sınamak içindir).
 * Recommendation ile gerçek entegrasyon DEĞİLDİR; tüketici kendi sözleşme testinde ayrıca sınanır.
 */
@SpringBootTest(properties = {"logging.level.root=INFO", "debug=false"})
@AutoConfigureMockMvc
class PerformanceEvidenceIntegrationTest extends PostgresIntegrationSupport {
    static final String INTERNAL = "/internal/v1/accounts/";
    static final String RES = "1920x1080";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    @AfterEach
    void resetPeers() { PerformancePeerDoubles.resetBehaviour(); }

    record Owner(UUID id, UUID link, UUID game, UUID profile) { }

    Owner owner() {
        UUID id = UUID.randomUUID();
        UUID link = UUID.randomUUID();
        UUID device = UUID.randomUUID();
        jdbc.update("INSERT INTO sync_consents(user_id,status,granted_at,version,created_at,updated_at) "
                + "VALUES (?,'GRANTED',now(),0,now(),now())", id);
        jdbc.update("INSERT INTO device_links(id,auth_device_id,user_id,label,status,linked_at,version,created_at,"
                + "updated_at) VALUES (?,?,?,?,'ACTIVE',now(),0,now(),now())", link, device, id, "Masaüstü PC");
        return new Owner(id, link, UUID.randomUUID(), UUID.randomUUID());
    }

    /** COMPLETED, bağlamlı oturum; FPS örnekleri 1 sn aralıkla 50 + (i mod 11). */
    UUID seed(Owner o, Instant endedAt, long version, String resolution, String preset, int samples, double outlier,
            int outliers) {
        return seed(o, o.game(), o.profile(), "COMPLETED", endedAt, version, resolution, preset, samples, outlier, outliers, true);
    }

    UUID seed(Owner o, UUID game, UUID profile, String status, Instant endedAt, long version, String resolution,
            String preset, int samples, double outlier, int outliers, boolean context) {
        UUID id = UUID.randomUUID();
        Instant start = endedAt.minusSeconds(1000);
        jdbc.update("""
                INSERT INTO performance_sessions(id,user_id,device_link_id,game_id,game_title,hardware_profile_id,source,
                    status,started_at,ended_at,version,created_at,updated_at,measurement_context_id,
                    hardware_profile_version,measurement_resolution,measurement_graphics_preset,measurement_rendering_mode)
                VALUES (?,?,?,?,'Oyun',?,'MANUAL',?,?,?,0,now(),now(),?,?,?,?,?)
                """, id, o.id(), o.link(), game, profile, status, Timestamp.from(start),
                "ACTIVE".equals(status) ? null : Timestamp.from(endedAt), context ? id : null, context ? version : null,
                context ? resolution : null, context ? preset : null, context ? "NATIVE_RASTER" : null);
        jdbc.update("""
                INSERT INTO performance_samples(id,session_id,user_id,device_link_id,batch_id,metric,value,captured_at,
                    received_at,source,measurement_context_id)
                SELECT gen_random_uuid(),?,?,?,gen_random_uuid(),'FPS',
                    CASE WHEN g >= ? THEN ? ELSE 50 + (g % 11) END,
                    ?::timestamptz + g * interval '1 second', ?::timestamptz + g * interval '1 second','DEVICE',?
                FROM generate_series(0, ? - 1 + ?) AS g
                """, id, o.id(), o.link(), samples, outlier, Timestamp.from(start.plusSeconds(5)),
                Timestamp.from(start.plusSeconds(5)), context ? id : null, samples, outliers);
        jdbc.update("UPDATE performance_sessions SET sample_count=(SELECT count(*) FROM performance_samples "
                + "WHERE session_id=?) WHERE id=?", id, id);
        return id;
    }

    String url(Owner o, UUID game, UUID profile, long version, String resolution, String preset, String mode) {
        return INTERNAL + o.id() + "/games/" + game + "/performance-evidence?hardwareProfileId=" + profile
                + "&hardwareProfileVersion=" + version + "&resolution=" + resolution + "&graphicsPreset=" + preset
                + "&renderingMode=" + mode;
    }

    String url(Owner o) { return url(o, o.game(), o.profile(), 3, RES, "HIGH", "NATIVE_RASTER"); }

    static String token(UUID account) throws Exception {
        return "Bearer " + PerformanceTestAuth.AUTH.performanceClientToken("recommendation-service", account);
    }

    JsonNode call(Owner o, String path, int expected) throws Exception {
        var response = mvc.perform(get(path).header("Authorization", token(o.id()))).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(expected);
        return json.readTree(response.getContentAsString());
    }

    void threeSessions(Owner o, int samples) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        for (int i = 1; i <= 3; i++) seed(o, now.minus(i, ChronoUnit.DAYS), 3, RES, "HIGH", samples, 0, 0);
    }

    @Test
    void threeEligibleSessionsProduceMedianOfSessionP10AndP90WithProvenance() throws Exception {
        var o = owner();
        threeSessions(o, 110);
        var response = mvc.perform(get(url(o)).header("Authorization", token(o.id()))).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
        var body = json.readTree(response.getContentAsString());
        assertThat(body.get("status").asString()).isEqualTo("SUFFICIENT");
        assertThat(body.get("contractVersion").asString()).isEqualTo("fps-evidence-v1");
        assertThat(body.get("lowerReferenceFps").asDouble()).isEqualTo(51.0);
        assertThat(body.get("upperReferenceFps").asDouble()).isEqualTo(59.0);
        assertThat(body.get("sessionCount").asInt()).isEqualTo(3);
        assertThat(body.get("consideredSessionCount").asInt()).isEqualTo(3);
        assertThat(body.get("observedSampleCount").asInt()).isEqualTo(330);
        assertThat(body.get("inlierSampleCount").asInt()).isEqualTo(330);
        assertThat(body.get("excludedSampleCount").asInt()).isZero();
        assertThat(body.get("windowLimited").asBoolean()).isFalse();
        assertThat(body.get("source").asString()).isEqualTo("AUTHENTICATED_DEVICE");
        assertThat(body.get("contextVerification").asString()).isEqualTo("OWNER_SNAPSHOT_DEVICE_DECLARATION");
        assertThat(body.get("method").asString()).isEqualTo("SESSION_MEDIAN_P10_P90_IQR_1_5_V1");
        assertThat(body.get("confidenceCeiling").asString()).isEqualTo("LOW");
        assertThat(body.get("renderingMode").asString()).isEqualTo("NATIVE_RASTER");
        assertThat(body.get("hardwareProfileVersion").asLong()).isEqualTo(3);
        assertThat(body.get("limitations").size()).isEqualTo(4);
        assertThat(body.get("firstMeasuredAt").isNull()).isFalse();
        assertThat(body.get("lastMeasuredAt").isNull()).isFalse();
    }

    @Test
    void fewerThanThreeEligibleSessionsStayInsufficientWithNullRange() throws Exception {
        var o = owner();
        Instant now = Instant.now();
        seed(o, now.minus(1, ChronoUnit.DAYS), 3, RES, "HIGH", 110, 0, 0);
        seed(o, now.minus(2, ChronoUnit.DAYS), 3, RES, "HIGH", 110, 0, 0);
        var body = call(o, url(o), 200);
        assertThat(body.get("status").asString()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(body.get("sessionCount").asInt()).isEqualTo(2);
        assertThat(body.get("lowerReferenceFps").isNull()).isTrue();
        assertThat(body.get("upperReferenceFps").isNull()).isTrue();
    }

    @Test
    void noMeasurementsAtAllIsAnHonestEmptyAnswer() throws Exception {
        var o = owner();
        var body = call(o, url(o), 200);
        assertThat(body.get("status").asString()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(body.get("consideredSessionCount").asInt()).isZero();
        assertThat(body.get("observedSampleCount").asInt()).isZero();
        assertThat(body.get("firstMeasuredAt").isNull()).isTrue();
    }

    @Test
    void sixtyInliersAndSixtySecondsAreTheEligibilityBoundary() throws Exception {
        var o = owner();
        threeSessions(o, 60);
        // 60 örnek = 59 sn yayılım: uygun değil.
        assertThat(call(o, url(o), 200).get("status").asString()).isEqualTo("INSUFFICIENT_DATA");
        jdbc.update("DELETE FROM performance_sessions WHERE user_id=?", o.id());
        threeSessions(o, 61);
        var body = call(o, url(o), 200);
        assertThat(body.get("status").asString()).isEqualTo("SUFFICIENT");
        assertThat(body.get("sessionCount").asInt()).isEqualTo(3);
        jdbc.update("DELETE FROM performance_sessions WHERE user_id=?", o.id());
        threeSessions(o, 59);
        assertThat(call(o, url(o), 200).get("status").asString()).isEqualTo("INSUFFICIENT_DATA");
    }

    @Test
    void outliersAreExcludedByIqrAndCountedAndNeverReachTheRange() throws Exception {
        var o = owner();
        Instant now = Instant.now();
        for (int i = 1; i <= 3; i++) seed(o, now.minus(i, ChronoUnit.DAYS), 3, RES, "HIGH", 110, 900, 3);
        var body = call(o, url(o), 200);
        assertThat(body.get("status").asString()).isEqualTo("SUFFICIENT");
        assertThat(body.get("observedSampleCount").asInt()).isEqualTo(339);
        assertThat(body.get("inlierSampleCount").asInt()).isEqualTo(330);
        assertThat(body.get("excludedSampleCount").asInt()).isEqualTo(9);
        assertThat(body.get("upperReferenceFps").asDouble()).isLessThanOrEqualTo(60.0);
        assertThat(body.get("lowerReferenceFps").asDouble()).isGreaterThanOrEqualTo(50.0);
    }

    @Test
    void differentContextsNeverMixIntoTheAnswer() throws Exception {
        var o = owner();
        threeSessions(o, 110);
        for (String other : List.of(
                url(o, o.game(), o.profile(), 4, RES, "HIGH", "NATIVE_RASTER"),
                url(o, o.game(), o.profile(), 3, "2560x1440", "HIGH", "NATIVE_RASTER"),
                url(o, o.game(), o.profile(), 3, RES, "LOW", "NATIVE_RASTER"),
                url(o, o.game(), UUID.randomUUID(), 3, RES, "HIGH", "NATIVE_RASTER"),
                url(o, UUID.randomUUID(), o.profile(), 3, RES, "HIGH", "NATIVE_RASTER"))) {
            var body = call(o, other, 200);
            assertThat(body.get("status").asString()).as(other).isEqualTo("INSUFFICIENT_DATA");
            assertThat(body.get("consideredSessionCount").asInt()).as(other).isZero();
        }
    }

    @Test
    void legacyActiveOldAndForeignSessionsAreNotEvidence() throws Exception {
        var o = owner();
        var foreign = owner();
        Instant now = Instant.now();
        seed(o, o.game(), o.profile(), "COMPLETED", now.minus(1, ChronoUnit.DAYS), 3, RES, "HIGH", 110, 0, 0, false);
        seed(o, o.game(), o.profile(), "ACTIVE", now.minus(1, ChronoUnit.DAYS), 3, RES, "HIGH", 110, 0, 0, true);
        seed(o, now.minus(31, ChronoUnit.DAYS), 3, RES, "HIGH", 110, 0, 0);
        seed(foreign, o.game(), o.profile(), "COMPLETED", now.minus(1, ChronoUnit.DAYS), 3, RES, "HIGH", 110, 0, 0, true);
        var body = call(o, url(o), 200);
        assertThat(body.get("consideredSessionCount").asInt()).isZero();
        assertThat(body.get("status").asString()).isEqualTo("INSUFFICIENT_DATA");
    }

    @Test
    void queryIsBoundedToTwentySessionsAndReportsTheWindowLimit() throws Exception {
        var o = owner();
        Instant now = Instant.now();
        for (int i = 0; i < 22; i++) seed(o, now.minus(i + 1, ChronoUnit.HOURS), 3, RES, "HIGH", 70, 0, 0);
        var body = call(o, url(o), 200);
        assertThat(body.get("consideredSessionCount").asInt()).isEqualTo(20);
        assertThat(body.get("windowLimited").asBoolean()).isTrue();
    }

    @Test
    void perSessionSampleCapIsSixHundred() throws Exception {
        var o = owner();
        Instant now = Instant.now();
        for (int i = 1; i <= 3; i++) seed(o, now.minus(i, ChronoUnit.DAYS), 3, RES, "HIGH", 700, 0, 0);
        var body = call(o, url(o), 200);
        assertThat(body.get("observedSampleCount").asInt()).isEqualTo(1800);
    }

    @Test
    void serviceIdentityAccountBindingAndParametersAreEnforced() throws Exception {
        var o = owner();
        String path = url(o);
        mvc.perform(get(path)).andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(401));
        mvc.perform(get(path).header("Authorization", "Bearer " + PerformanceTestAuth.AUTH.token(o.id(), "PLAYER")))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(401));
        // Başka hesabın token'ı, hardware servisi ve yanlış kapsam kanıt okuyamaz.
        mvc.perform(get(path).header("Authorization", token(UUID.randomUUID())))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(403));
        mvc.perform(get(path).header("Authorization", "Bearer "
                        + PerformanceTestAuth.AUTH.performanceClientToken("hardware-service", o.id())))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(403));
        mvc.perform(post(path).header("Authorization", token(o.id())))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isIn(403, 405));
        for (String bad : List.of(
                url(o, o.game(), o.profile(), 3, "1920x1080x2", "HIGH", "NATIVE_RASTER"),
                url(o, o.game(), o.profile(), 3, "99x1080", "HIGH", "NATIVE_RASTER"),
                url(o, o.game(), o.profile(), 3, "20000x1080", "HIGH", "NATIVE_RASTER"),
                url(o, o.game(), o.profile(), 3, RES, "ULTRAX", "NATIVE_RASTER"),
                url(o, o.game(), o.profile(), 3, RES, "HIGH", "RAY_TRACING"),
                url(o, o.game(), o.profile(), -1, RES, "HIGH", "NATIVE_RASTER"),
                path.replace("hardwareProfileVersion=3", "hardwareProfileVersion=abc"),
                path.replaceAll("&renderingMode=NATIVE_RASTER", ""))) {
            assertThat(call(o, bad, 400).get("code").asString()).as(bad).isEqualTo("INVALID_REQUEST");
        }
    }

    @Test
    void databaseRejectsPartialOrMismatchedContextsAndCrossSessionSamples() {
        var o = owner();
        Instant now = Instant.now();
        UUID id = UUID.randomUUID();
        String insert = """
                INSERT INTO performance_sessions(id,user_id,device_link_id,game_id,game_title,hardware_profile_id,source,
                    status,started_at,ended_at,version,created_at,updated_at,measurement_context_id,
                    hardware_profile_version,measurement_resolution,measurement_graphics_preset,measurement_rendering_mode)
                VALUES (?,?,?,?,'Oyun',?,'MANUAL','COMPLETED',now(),now(),0,now(),now(),?,?,?,?,?)
                """;
        // Kısmi bağlam
        Object[] partial = {id, o.id(), o.link(), o.game(), o.profile(), id, 1L, null, "HIGH", "NATIVE_RASTER"};
        assertThatThrownBy(() -> jdbc.update(insert, partial)).isInstanceOf(DataIntegrityViolationException.class);
        // Bağlam kimliği oturum kimliğinden farklı
        Object[] foreignContext = {id, o.id(), o.link(), o.game(), o.profile(), UUID.randomUUID(), 1L, RES, "HIGH",
            "NATIVE_RASTER"};
        assertThatThrownBy(() -> jdbc.update(insert, foreignContext)).isInstanceOf(DataIntegrityViolationException.class);
        // Geçersiz değerler
        for (Object[] bad : new Object[][]{{RES, "EPIC", "NATIVE_RASTER"}, {RES, "HIGH", "UPSCALED"},
                {"99999999x1080", "HIGH", "NATIVE_RASTER"}, {"20000x1080", "HIGH", "NATIVE_RASTER"}}) {
            assertThatThrownBy(() -> jdbc.update(insert, id, o.id(), o.link(), o.game(), o.profile(), id, 1L, bad[0],
                    bad[1], bad[2])).isInstanceOf(DataIntegrityViolationException.class);
        }
        // Profil bağlantısı olmadan bağlam yok
        assertThatThrownBy(() -> jdbc.update(insert, id, o.id(), o.link(), o.game(), null, id, 1L, RES, "HIGH",
                "NATIVE_RASTER")).isInstanceOf(DataIntegrityViolationException.class);
        // Örnek bağlamı başka oturumun bağlamını gösteremez
        UUID a = seed(o, now.minus(1, ChronoUnit.DAYS), 3, RES, "HIGH", 5, 0, 0);
        UUID b = seed(o, now.minus(2, ChronoUnit.DAYS), 3, RES, "HIGH", 5, 0, 0);
        assertThatThrownBy(() -> jdbc.update("UPDATE performance_samples SET measurement_context_id=? WHERE session_id=?",
                b, a)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void evidenceQueryUsesTheDedicatedIndexes() {
        var o = owner();
        Instant now = Instant.now();
        for (int i = 1; i <= 3; i++) seed(o, now.minus(i, ChronoUnit.DAYS), 3, RES, "HIGH", 110, 0, 0);
        List<String> indexes = jdbc.queryForList("SELECT indexname FROM pg_indexes WHERE tablename IN "
                + "('performance_sessions','performance_samples') AND indexname LIKE '%evidence%' OR "
                + "indexname LIKE '%context_fps%'", String.class);
        assertThat(indexes).contains("ix_performance_sessions_evidence", "ix_performance_samples_context_fps");
    }
}

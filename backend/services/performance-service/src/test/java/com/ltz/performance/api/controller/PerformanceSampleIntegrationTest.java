package com.ltz.performance.api.controller;

import com.ltz.performance.infrastructure.client.PerformancePeerDoubles;
import com.ltz.performance.infrastructure.persistence.PostgresIntegrationSupport;
import com.ltz.performance.infrastructure.security.PerformanceTestAuth;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * PERF-05..12: örnek (FPS, sıcaklık, kullanım, RAM/VRAM, disk, fan) kabulü. Gerçek Auth issuer/JWKS (üretim
 * kaynaklarından derlenmiş), Performance HTTP güvenliği, servisler ve geçici PostgreSQL kullanılır. Cihaz listesi
 * (Auth) ve Game/Hardware YEREL SAPMADIR; performance-service hedefli cihaz token'ı yerel imzalıdır (Auth bugün yalnız
 * aud=hardware-service üretir, REQ-01-A). Gerçek masaüstü sensör sözleşmesi henüz yoktur. Bu test, dağıtılmış Auth,
 * Game, Hardware veya gerçek masaüstü istemcisiyle uçtan uca entegrasyon DEĞİLDİR.
 */
@SpringBootTest(properties = {"logging.level.root=INFO", "debug=false"})
@AutoConfigureMockMvc
class PerformanceSampleIntegrationTest extends PostgresIntegrationSupport {
    static final String CONSENT = "/api/v1/performance/sync-consent";
    static final String DEVICES = "/api/v1/performance/devices";
    static final String SESSIONS = "/api/v1/performance/sessions";
    static final String BATCHES = "/api/v1/performance/sync/batches";
    static final List<String> METRICS = List.of("CPU_TEMP_C", "GPU_TEMP_C", "CPU_USAGE_PERCENT", "GPU_USAGE_PERCENT",
            "RAM_USED_MB", "VRAM_USED_MB", "DISK_READ_MBPS", "DISK_WRITE_MBPS", "FAN_RPM", "FPS");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    @AfterEach
    void resetPeers() { PerformancePeerDoubles.resetBehaviour(); }

    /** Bir hesap, onaylı ve bağlı bir cihaz ve o hesabın ACTIVE oturumu. */
    record Rig(UUID account, UUID device, UUID link, UUID session) { }

    // ---------- yardımcılar ----------

    static String userBearer(UUID account) throws Exception {
        return "Bearer " + PerformanceTestAuth.AUTH.token(account, "PLAYER");
    }

    static String deviceBearer(Rig rig) throws Exception {
        return "Bearer " + PerformanceTestAuth.performanceDeviceToken(rig.device(), rig.account());
    }

    JsonNode send(MockHttpServletRequestBuilder request, int expected) throws Exception {
        var response = mvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(expected);
        var body = response.getContentAsString();
        return body.isBlank() ? null : json.readTree(body);
    }

    UUID startSession(UUID account) throws Exception {
        UUID gameId = UUID.randomUUID();
        PerformancePeerDoubles.registerGame(gameId, "Test Oyunu");
        var created = send(post(SESSIONS).header("Authorization", userBearer(account))
                .contentType(MediaType.APPLICATION_JSON).content("{\"gameId\":\"" + gameId + "\"}"), 201);
        return UUID.fromString(created.get("id").asString());
    }

    Rig rig() throws Exception {
        UUID account = UUID.randomUUID();
        UUID device = UUID.randomUUID();
        PerformanceTestAuth.authDevices(account, device, "Masaüstü PC");
        send(put(CONSENT).header("Authorization", userBearer(account)), 200);
        var linked = send(post(DEVICES).header("Authorization", userBearer(account))
                .contentType(MediaType.APPLICATION_JSON).content("{\"deviceId\":\"" + device + "\"}"), 200);
        UUID link = UUID.fromString(linked.get("id").asString());
        return new Rig(account, device, link, startSession(account));
    }

    static Instant base() { return Instant.now().truncatedTo(ChronoUnit.MILLIS).minusSeconds(60); }

    static String sample(Instant at, String metric, String value) {
        return "{\"capturedAt\":\"" + at + "\",\"metric\":\"" + metric + "\",\"value\":" + value + "}";
    }

    static String v2(UUID session, String... samples) {
        return "{\"schemaVersion\":2,\"clientSentAt\":\"" + Instant.now() + "\",\"sessionId\":\"" + session
                + "\",\"samples\":[" + String.join(",", samples) + "]}";
    }

    MockHttpServletRequestBuilder push(Rig rig, UUID key, String body) throws Exception {
        return post(BATCHES).header("Authorization", deviceBearer(rig)).header("Idempotency-Key", key.toString())
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    JsonNode pushOk(Rig rig, String body) throws Exception { return send(push(rig, UUID.randomUUID(), body), 200); }

    JsonNode pushFails(Rig rig, String body, int status, String code) throws Exception {
        var problem = send(push(rig, UUID.randomUUID(), body), status);
        assertThat(problem.get("code").asString()).isEqualTo(code);
        return problem;
    }

    int rows(UUID session) {
        return jdbc.queryForObject("SELECT count(*) FROM performance_samples WHERE session_id=?", Integer.class, session);
    }

    int counter(UUID session) {
        return jdbc.queryForObject("SELECT sample_count FROM performance_sessions WHERE id=?", Integer.class, session);
    }

    // ---------- kabul: tüm metrikler ----------

    @Test
    void allTenMetricsAreStoredWithServerFieldsAndSessionCounter() throws Exception {
        var rig = rig();
        Instant at = base();
        String[] values = {"61.5", "70", "35.25", "99", "8192", "6144", "120.5", "80", "1450", "143.9"};
        var samples = new String[METRICS.size()];
        for (int i = 0; i < samples.length; i++) samples[i] = sample(at.plusMillis(i), METRICS.get(i), values[i]);

        var result = pushOk(rig, v2(rig.session(), samples));

        assertThat(result.get("sampleCount").asInt()).isEqualTo(10);
        assertThat(result.get("storedSamples").asInt()).isEqualTo(10);
        assertThat(result.get("duplicateSamples").asInt()).isZero();
        assertThat(result.get("replayed").asBoolean()).isFalse();
        assertThat(rows(rig.session())).isEqualTo(10);
        assertThat(counter(rig.session())).isEqualTo(10);
        var row = jdbc.queryForMap("""
                SELECT user_id, device_link_id, source, batch_id, received_at, captured_at, value
                FROM performance_samples WHERE session_id=? AND metric='FPS'
                """, rig.session());
        assertThat(row).containsEntry("source", "DEVICE").containsEntry("user_id", rig.account())
                .containsEntry("device_link_id", rig.link()).containsEntry("value", 143.9);
        assertThat(row.get("batch_id")).hasToString(result.get("batchId").asString());
        var receipt = jdbc.queryForMap("SELECT session_id, stored_count, duplicate_count, sample_count, schema_version "
                + "FROM sync_batches WHERE id=?", UUID.fromString(result.get("batchId").asString()));
        assertThat(receipt).containsEntry("session_id", rig.session()).containsEntry("stored_count", 10)
                .containsEntry("duplicate_count", 0).containsEntry("sample_count", 10)
                .containsEntry("schema_version", 2);
        var detail = send(get(SESSIONS + "/" + rig.session()).header("Authorization", userBearer(rig.account())), 200);
        assertThat(detail.get("sampleCount").asInt()).isEqualTo(10);
    }

    @Test
    void unorderedTimestampsAreAccepted() throws Exception {
        var rig = rig();
        Instant at = base();
        var result = pushOk(rig, v2(rig.session(), sample(at.plusSeconds(5), "FPS", "60"),
                sample(at, "FPS", "58"), sample(at.plusSeconds(2), "FPS", "59")));
        assertThat(result.get("storedSamples").asInt()).isEqualTo(3);
    }

    @Test
    void emptyV2BatchIsAcceptedButStillRequiresOwnedSession() throws Exception {
        var rig = rig();
        var result = pushOk(rig, v2(rig.session()));
        assertThat(result.get("storedSamples").asInt()).isZero();
        assertThat(result.get("sampleCount").asInt()).isZero();
        pushFails(rig, v2(UUID.randomUUID()), 404, "PERFORMANCE_NOT_FOUND");
    }

    // ---------- değer sınırları ----------

    @Test
    void zeroIsAValidReadingOnlyWhereTheSensorCanReportIt() throws Exception {
        var rig = rig();
        Instant at = base();
        var result = pushOk(rig, v2(rig.session(), sample(at, "CPU_USAGE_PERCENT", "0"),
                sample(at, "GPU_USAGE_PERCENT", "0"), sample(at, "FAN_RPM", "0"), sample(at, "RAM_USED_MB", "0"),
                sample(at, "DISK_READ_MBPS", "0"), sample(at, "DISK_WRITE_MBPS", "0"),
                sample(at, "VRAM_USED_MB", "0")));
        assertThat(result.get("storedSamples").asInt()).isEqualTo(7);
        // Sıcaklık ve FPS için 0 ölçülememiş sensör demektir; saklanmaz.
        for (String metric : List.of("CPU_TEMP_C", "GPU_TEMP_C", "FPS")) {
            var problem = pushFails(rig, v2(rig.session(), sample(at, metric, "0")), 422, "SAMPLE_VALUE_INVALID");
            assertThat(problem.get("sampleIndex").asInt()).isZero();
        }
    }

    @Test
    void outOfRangeValuesRejectTheWholeBatchWithTheOffendingIndex() throws Exception {
        var rig = rig();
        Instant at = base();
        String[][] invalid = {{"CPU_TEMP_C", "150.01"}, {"GPU_TEMP_C", "-3"}, {"CPU_USAGE_PERCENT", "100.1"},
            {"GPU_USAGE_PERCENT", "-0.1"}, {"RAM_USED_MB", "-1"}, {"VRAM_USED_MB", "262145"},
            {"DISK_READ_MBPS", "-5"}, {"DISK_WRITE_MBPS", "100001"}, {"FAN_RPM", "-1"}, {"FAN_RPM", "30001"},
            {"FPS", "-60"}, {"FPS", "1000.5"}};
        // Hız sınırını (cihaz başına dakikada 30) aşmamak için örnekleri tek gönderimde ayırmadan, gruplayarak dene.
        for (int i = 0; i < invalid.length; i += 4) {
            for (int j = i; j < Math.min(i + 4, invalid.length); j++) {
                var problem = pushFails(rig, v2(rig.session(), sample(at, "FPS", "60"),
                        sample(at.plusMillis(1), invalid[j][0], invalid[j][1])), 422, "SAMPLE_VALUE_INVALID");
                assertThat(problem.get("sampleIndex").asInt()).as(invalid[j][0] + "=" + invalid[j][1]).isEqualTo(1);
            }
        }
        assertThat(rows(rig.session())).as("hep-ya-hiç: geçerli ilk örnek de yazılmamalı").isZero();
        assertThat(counter(rig.session())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sync_batches WHERE user_id=?", Integer.class,
                rig.account())).isZero();
    }

    @Test
    void boundaryValuesAreAccepted() throws Exception {
        var rig = rig();
        Instant at = base();
        var result = pushOk(rig, v2(rig.session(), sample(at, "CPU_TEMP_C", "150"), sample(at, "GPU_TEMP_C", "0.001"),
                sample(at, "CPU_USAGE_PERCENT", "100"), sample(at, "FPS", "1000"), sample(at, "FAN_RPM", "30000"),
                sample(at, "RAM_USED_MB", "1048576"), sample(at, "VRAM_USED_MB", "262144"),
                sample(at, "DISK_READ_MBPS", "100000")));
        assertThat(result.get("storedSamples").asInt()).isEqualTo(8);
    }

    @Test
    void malformedNumbersAndShapesAreRejectedAndNeverStoredAsZero() throws Exception {
        var rig = rig();
        String at = base().toString();
        String head = "{\"schemaVersion\":2,\"clientSentAt\":\"" + Instant.now() + "\",\"sessionId\":\""
                + rig.session() + "\",\"samples\":[";
        List<String> bad = List.of(
                "{\"capturedAt\":\"" + at + "\",\"metric\":\"FPS\",\"value\":null}",
                "{\"capturedAt\":\"" + at + "\",\"metric\":\"FPS\"}",
                "{\"capturedAt\":\"" + at + "\",\"metric\":\"FPS\",\"value\":\"abc\"}",
                "{\"capturedAt\":\"" + at + "\",\"metric\":\"FPS\",\"value\":\"60\"}",
                "{\"capturedAt\":\"" + at + "\",\"metric\":\"FPS\",\"value\":[60]}",
                "{\"capturedAt\":\"" + at + "\",\"metric\":\"FPS\",\"value\":{\"v\":60}}",
                "{\"capturedAt\":\"" + at + "\",\"metric\":\"FPS\",\"value\":true}",
                "{\"capturedAt\":\"nope\",\"metric\":\"FPS\",\"value\":60}",
                "{\"metric\":\"FPS\",\"value\":60}",
                "{\"capturedAt\":\"" + at + "\",\"value\":60}",
                "{\"capturedAt\":\"" + at + "\",\"metric\":null,\"value\":60}",
                "{\"capturedAt\":\"" + at + "\",\"metric\":\"FPS\",\"value\":60,\"unit\":\"FPS\"}",
                "null");
        for (String item : bad) {
            send(post(BATCHES).header("Authorization", deviceBearer(rig)).header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON).content(head + item + "]}"), 400);
        }
        // JSON'da NaN/Infinity sabitleri yoktur; aşırı büyük üs sonsuz olsa da reddedilir (400 veya 422), asla saklanmaz.
        var huge = send(push(rig, UUID.randomUUID(), v2(rig.session(), sample(Instant.parse(at), "FPS", "1e999"))), 422);
        assertThat(huge.get("code").asString()).isEqualTo("SAMPLE_VALUE_INVALID");
        assertThat(rows(rig.session())).isZero();
    }

    @Test
    void unsupportedOrDifferentlyCasedMetricsAreRejectedWithIndex() throws Exception {
        var rig = rig();
        Instant at = base();
        for (String metric : List.of("TEMPERATURE", "fps", "Fps", "CPU_TEMP_F", "")) {
            var problem = pushFails(rig, v2(rig.session(), sample(at, "FPS", "60"),
                    sample(at.plusMillis(1), metric, "50")), 422, "SAMPLE_METRIC_UNSUPPORTED");
            assertThat(problem.get("sampleIndex").asInt()).as(metric).isEqualTo(1);
        }
        assertThat(rows(rig.session())).isZero();
    }

    // ---------- zaman damgaları ----------

    @Test
    void timestampsOutsideTheSessionWindowAreRejected() throws Exception {
        var rig = rig();
        Instant now = Instant.now();
        pushFails(rig, v2(rig.session(), sample(now.plusSeconds(6 * 60), "FPS", "60")), 422, "SAMPLE_TIME_INVALID");
        pushFails(rig, v2(rig.session(), sample(now.minusSeconds(6 * 60), "FPS", "60")), 422, "SAMPLE_TIME_INVALID");
        pushFails(rig, v2(rig.session(), sample(now.minus(3650, ChronoUnit.DAYS), "FPS", "60")), 422,
                "SAMPLE_TIME_INVALID");
        pushFails(rig, v2(rig.session(), sample(Instant.EPOCH, "FPS", "60")), 422, "SAMPLE_TIME_INVALID");
        assertThat(rows(rig.session())).isZero();
        // Saat sapması payı içinde kalanlar kabul edilir: oturumdan 4 dk önce ve gelecekten 4 dk sonra.
        var ok = pushOk(rig, v2(rig.session(), sample(now.minusSeconds(4 * 60), "FPS", "60"),
                sample(now.plusSeconds(4 * 60), "FPS", "61")));
        assertThat(ok.get("storedSamples").asInt()).isEqualTo(2);
    }

    @Test
    void duplicateMetricAndTimeInsideOneBatchIsRejected() throws Exception {
        var rig = rig();
        Instant at = base();
        var problem = pushFails(rig, v2(rig.session(), sample(at, "FPS", "60"), sample(at, "CPU_TEMP_C", "60"),
                sample(at, "FPS", "61")), 422, "SAMPLE_DUPLICATE_IN_BATCH");
        assertThat(problem.get("sampleIndex").asInt()).isEqualTo(2);
        // Aynı metrik farklı an ve aynı an farklı metrik çoğaltma değildir.
        assertThat(pushOk(rig, v2(rig.session(), sample(at, "FPS", "60"), sample(at.plusMillis(1), "FPS", "61"),
                sample(at, "CPU_TEMP_C", "60"))).get("storedSamples").asInt()).isEqualTo(3);
    }

    // ---------- çoğaltma ve idempotency ----------

    @Test
    void sameReadingsUnderADifferentKeyAreNotDuplicated() throws Exception {
        var rig = rig();
        Instant at = base();
        var first = pushOk(rig, v2(rig.session(), sample(at, "FPS", "60"), sample(at.plusSeconds(1), "FPS", "61")));
        assertThat(first.get("storedSamples").asInt()).isEqualTo(2);
        var overlap = pushOk(rig, v2(rig.session(), sample(at.plusSeconds(1), "FPS", "99"),
                sample(at.plusSeconds(2), "FPS", "62")));
        assertThat(overlap.get("storedSamples").asInt()).isEqualTo(1);
        assertThat(overlap.get("duplicateSamples").asInt()).isEqualTo(1);
        var again = pushOk(rig, v2(rig.session(), sample(at, "FPS", "60"), sample(at.plusSeconds(1), "FPS", "61")));
        assertThat(again.get("storedSamples").asInt()).isZero();
        assertThat(again.get("duplicateSamples").asInt()).isEqualTo(2);
        assertThat(rows(rig.session())).isEqualTo(3);
        assertThat(counter(rig.session())).isEqualTo(3);
        // İlk yazılan değer korunur (ezilmez).
        assertThat(jdbc.queryForObject("SELECT value FROM performance_samples WHERE session_id=? AND captured_at=?",
                Double.class, rig.session(), java.sql.Timestamp.from(at.plusSeconds(1)))).isEqualTo(61.0);
    }

    @Test
    void replayReturnsFirstResultAndSameKeyWithDifferentContentConflicts() throws Exception {
        var rig = rig();
        Instant at = base();
        UUID key = UUID.randomUUID();
        String body = v2(rig.session(), sample(at, "FPS", "60"), sample(at.plusSeconds(1), "CPU_TEMP_C", "70"));
        var first = send(push(rig, key, body), 200);
        var replay = send(push(rig, key, body), 200);
        assertThat(replay.get("replayed").asBoolean()).isTrue();
        assertThat(replay.get("batchId").asString()).isEqualTo(first.get("batchId").asString());
        assertThat(replay.get("storedSamples").asInt()).isEqualTo(2);
        assertThat(rows(rig.session())).isEqualTo(2);
        assertThat(counter(rig.session())).isEqualTo(2);

        String changed = v2(rig.session(), sample(at, "FPS", "61"), sample(at.plusSeconds(1), "CPU_TEMP_C", "70"));
        var conflict = send(push(rig, key, changed), 409);
        assertThat(conflict.get("code").asString()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertThat(rows(rig.session())).isEqualTo(2);
        // Aynı anahtar başka oturum için de farklı içeriktir.
        var other = send(push(rig, key, v2(UUID.randomUUID(), sample(at, "FPS", "60"),
                sample(at.plusSeconds(1), "CPU_TEMP_C", "70"))), 409);
        assertThat(other.get("code").asString()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void failedBatchLeavesNoReceiptSoTheCorrectedBatchCanReuseTheKey() throws Exception {
        var rig = rig();
        Instant at = base();
        UUID key = UUID.randomUUID();
        send(push(rig, key, v2(rig.session(), sample(at, "FPS", "60"), sample(at, "CPU_TEMP_C", "0"))), 422);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sync_batches WHERE user_id=?", Integer.class,
                rig.account())).isZero();
        var fixed = send(push(rig, key, v2(rig.session(), sample(at, "FPS", "60"), sample(at, "CPU_TEMP_C", "55"))), 200);
        assertThat(fixed.get("replayed").asBoolean()).isFalse();
        assertThat(rows(rig.session())).isEqualTo(2);
    }

    @Test
    void concurrentSameKeyStoresOnceAndConcurrentOverlappingBatchesNeverDuplicate() throws Exception {
        var rig = rig();
        Instant at = base();
        var samples = new String[20];
        for (int i = 0; i < samples.length; i++) samples[i] = sample(at.plusMillis(i * 10L), "FPS", String.valueOf(50 + i));
        String body = v2(rig.session(), samples);
        UUID sameKey = UUID.randomUUID();
        List<Integer> statuses = new ArrayList<>();
        try (var pool = Executors.newFixedThreadPool(4)) {
            List<Callable<Integer>> calls = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                var request = push(rig, sameKey, body);
                calls.add(() -> mvc.perform(request).andReturn().getResponse().getStatus());
            }
            for (var future : pool.invokeAll(calls)) statuses.add(future.get());
        }
        assertThat(statuses).allMatch(code -> code == 200);
        assertThat(rows(rig.session())).isEqualTo(20);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sync_batches WHERE user_id=?", Integer.class,
                rig.account())).isEqualTo(1);

        // Aynı örnekler farklı anahtarlarla eşzamanlı: satır sayısı değişmez, sayaç tutarlı kalır.
        var totals = new ArrayList<Integer>();
        try (var pool = Executors.newFixedThreadPool(4)) {
            List<Callable<Integer>> calls = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                var request = push(rig, UUID.randomUUID(), body);
                calls.add(() -> {
                    var response = mvc.perform(request).andReturn().getResponse();
                    return response.getStatus() == 200
                            ? json.readTree(response.getContentAsString()).get("storedSamples").asInt() : -1;
                });
            }
            for (var future : pool.invokeAll(calls)) totals.add(future.get());
        }
        assertThat(totals).allMatch(stored -> stored == 0);
        assertThat(rows(rig.session())).isEqualTo(20);
        assertThat(counter(rig.session())).isEqualTo(20);
    }

    // ---------- şema uyumluluğu ----------

    @Test
    void schemaV1StaysBackwardCompatibleAndMixedShapesAreRejected() throws Exception {
        var rig = rig();
        String sent = Instant.now().toString();
        var v1 = send(push(rig, UUID.randomUUID(),
                "{\"schemaVersion\":1,\"clientSentAt\":\"" + sent + "\",\"sampleCount\":4}"), 200);
        assertThat(v1.get("sampleCount").asInt()).isEqualTo(4);
        assertThat(v1.get("storedSamples").asInt()).isZero();
        assertThat(v1.get("duplicateSamples").asInt()).isZero();
        assertThat(jdbc.queryForObject("SELECT session_id FROM sync_batches WHERE id=?", UUID.class,
                UUID.fromString(v1.get("batchId").asString()))).isNull();

        String s = sample(base(), "FPS", "60");
        List<String> mixed = List.of(
                "{\"schemaVersion\":1,\"clientSentAt\":\"" + sent + "\",\"sampleCount\":1,\"samples\":[" + s + "]}",
                "{\"schemaVersion\":1,\"clientSentAt\":\"" + sent + "\",\"sampleCount\":1,\"sessionId\":\""
                        + rig.session() + "\"}",
                "{\"schemaVersion\":1,\"clientSentAt\":\"" + sent + "\"}",
                "{\"schemaVersion\":2,\"clientSentAt\":\"" + sent + "\",\"sessionId\":\"" + rig.session() + "\"}",
                "{\"schemaVersion\":2,\"clientSentAt\":\"" + sent + "\",\"samples\":[" + s + "]}",
                "{\"schemaVersion\":2,\"clientSentAt\":\"" + sent + "\",\"sampleCount\":1,\"sessionId\":\""
                        + rig.session() + "\",\"samples\":[" + s + "]}",
                "{\"schemaVersion\":2,\"clientSentAt\":\"" + sent + "\",\"sessionId\":\"not-a-uuid\",\"samples\":[]}",
                "{\"schemaVersion\":3,\"clientSentAt\":\"" + sent + "\",\"sessionId\":\"" + rig.session()
                        + "\",\"samples\":[]}",
                "{\"schemaVersion\":0,\"clientSentAt\":\"" + sent + "\",\"sampleCount\":1}");
        for (String body : mixed) send(push(rig, UUID.randomUUID(), body), 400);
        assertThat(rows(rig.session())).isZero();
    }

    @Test
    void batchSizeLimitIsFiveHundredSamples() throws Exception {
        var rig = rig();
        Instant at = base();
        var many = new String[501];
        for (int i = 0; i < many.length; i++) many[i] = sample(at.plusMillis(i), "FPS", "60");
        send(push(rig, UUID.randomUUID(), v2(rig.session(), many)), 400);
        var five = new String[500];
        System.arraycopy(many, 0, five, 0, 500);
        var ok = send(push(rig, UUID.randomUUID(), v2(rig.session(), five)), 200);
        assertThat(ok.get("storedSamples").asInt()).isEqualTo(500);
        assertThat(counter(rig.session())).isEqualTo(500);
    }

    // ---------- sahiplik / kimlik ----------

    @Test
    void otherAccountsSessionLooksExactlyLikeAnUnknownSession() throws Exception {
        var mine = rig();
        var theirs = rig();
        Instant at = base();
        var foreign = pushFails(mine, v2(theirs.session(), sample(at, "FPS", "60")), 404, "PERFORMANCE_NOT_FOUND");
        var unknown = pushFails(mine, v2(UUID.randomUUID(), sample(at, "FPS", "60")), 404, "PERFORMANCE_NOT_FOUND");
        assertThat(foreign.get("detail")).isEqualTo(unknown.get("detail"));
        assertThat(rows(theirs.session())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sync_batches WHERE user_id=?", Integer.class,
                mine.account())).isZero();
    }

    @Test
    void userTokensCannotPushAndDeviceTokensCannotDeleteSamples() throws Exception {
        var rig = rig();
        send(post(BATCHES).header("Authorization", userBearer(rig.account()))
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content(v2(rig.session(), sample(base(), "FPS", "60"))), 401);
        send(delete(SESSIONS + "/" + rig.session() + "/samples").header("Authorization", deviceBearer(rig)), 401);
        send(delete(SESSIONS + "/" + rig.session() + "/samples"), 401);
        send(post(BATCHES).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(v2(rig.session())), 401);
        assertThat(rows(rig.session())).isZero();
    }

    @Test
    void revokedConsentAndRemovedLinkStopSampleIngestion() throws Exception {
        var rig = rig();
        Instant at = base();
        pushOk(rig, v2(rig.session(), sample(at, "FPS", "60")));
        send(delete(CONSENT).header("Authorization", userBearer(rig.account())), 200);
        pushFails(rig, v2(rig.session(), sample(at.plusSeconds(1), "FPS", "61")), 403, "SYNC_CONSENT_REQUIRED");
        send(put(CONSENT).header("Authorization", userBearer(rig.account())), 200);
        send(delete(DEVICES + "/" + rig.link()).header("Authorization", userBearer(rig.account())), 200);
        pushFails(rig, v2(rig.session(), sample(at.plusSeconds(1), "FPS", "61")), 403, "DEVICE_NOT_LINKED");
        assertThat(rows(rig.session())).isEqualTo(1);
    }

    // ---------- oturum durumu ----------

    @Test
    void completedSessionAcceptsBufferedSamplesOnlyDuringTheGraceWindow() throws Exception {
        var rig = rig();
        Instant at = base();
        send(post(SESSIONS + "/" + rig.session() + "/end").header("Authorization", userBearer(rig.account())), 200);
        assertThat(pushOk(rig, v2(rig.session(), sample(at, "FPS", "60"))).get("storedSamples").asInt()).isEqualTo(1);

        jdbc.update("UPDATE performance_sessions SET started_at=now()-interval '2 hours', "
                + "ended_at=now()-interval '8 minutes' WHERE id=?", rig.session());
        Instant before = Instant.now().minusSeconds(9 * 60);
        assertThat(pushOk(rig, v2(rig.session(), sample(before, "FPS", "58"))).get("storedSamples").asInt()).isEqualTo(1);
        // Bitiş + saat sapması payından sonraki okuma oturuma ait olamaz.
        pushFails(rig, v2(rig.session(), sample(Instant.now().minusSeconds(60), "FPS", "57")), 422, "SAMPLE_TIME_INVALID");

        jdbc.update("UPDATE performance_sessions SET ended_at=now()-interval '11 minutes' WHERE id=?", rig.session());
        pushFails(rig, v2(rig.session(), sample(before, "FPS", "59")), 409, "SESSION_NOT_ACCEPTING_SAMPLES");
        assertThat(rows(rig.session())).isEqualTo(2);
    }

    @Test
    void abandonedAndStaleActiveSessionsRejectSamplesWithoutBeingMutated() throws Exception {
        var rig = rig();
        Instant at = base();
        jdbc.update("UPDATE performance_sessions SET started_at=now()-interval '25 hours' WHERE id=?", rig.session());
        pushFails(rig, v2(rig.session(), sample(at, "FPS", "60")), 409, "SESSION_NOT_ACCEPTING_SAMPLES");
        assertThat(jdbc.queryForObject("SELECT status FROM performance_sessions WHERE id=?", String.class,
                rig.session())).isEqualTo("ACTIVE");

        var second = rig();
        jdbc.update("UPDATE performance_sessions SET status='ABANDONED', ended_at=started_at+interval '1 second' "
                + "WHERE id=?", second.session());
        pushFails(second, v2(second.session(), sample(at, "FPS", "60")), 409, "SESSION_NOT_ACCEPTING_SAMPLES");
        assertThat(rows(second.session())).isZero();
    }

    // ---------- kotalar ----------

    @Test
    void sessionSampleLimitIsEnforcedAtomically() throws Exception {
        var rig = rig();
        Instant at = base();
        jdbc.update("UPDATE performance_sessions SET sample_count=99999 WHERE id=?", rig.session());
        pushFails(rig, v2(rig.session(), sample(at, "FPS", "60"), sample(at.plusMillis(1), "FPS", "61")), 409,
                "SESSION_SAMPLE_LIMIT_REACHED");
        assertThat(rows(rig.session())).isZero();
        assertThat(counter(rig.session())).isEqualTo(99999);
        pushOk(rig, v2(rig.session(), sample(at, "FPS", "60")));
        assertThat(counter(rig.session())).isEqualTo(100000);
        pushFails(rig, v2(rig.session(), sample(at.plusMillis(5), "FPS", "60")), 409, "SESSION_SAMPLE_LIMIT_REACHED");
    }

    @Test
    void accountSampleQuotaCoversAllOfTheUsersSessions() throws Exception {
        var rig = rig();
        UUID account = rig.account();
        send(post(SESSIONS + "/" + rig.session() + "/end").header("Authorization", userBearer(account)), 200);
        List<UUID> finished = new ArrayList<>(List.of(rig.session()));
        for (int i = 0; i < 4; i++) {
            UUID session = startSession(account);
            send(post(SESSIONS + "/" + session + "/end").header("Authorization", userBearer(account)), 200);
            finished.add(session);
        }
        UUID active = startSession(account);
        for (UUID session : finished) {
            jdbc.update("UPDATE performance_sessions SET sample_count=100000 WHERE id=?", session);
        }
        var active1 = new Rig(account, rig.device(), rig.link(), active);
        pushFails(active1, v2(active, sample(base(), "FPS", "60")), 409, "USER_SAMPLE_QUOTA_REACHED");
        // Örnekleri silmek kotayı serbest bırakır.
        send(delete(SESSIONS + "/" + finished.get(0) + "/samples").header("Authorization", userBearer(account)), 204);
        assertThat(pushOk(active1, v2(active, sample(base(), "FPS", "60"))).get("storedSamples").asInt()).isEqualTo(1);
    }

    // ---------- silme ----------

    @Test
    void deletingSamplesKeepsSessionAndIsIdempotentAndOwnerOnly() throws Exception {
        var rig = rig();
        var other = rig();
        Instant at = base();
        pushOk(rig, v2(rig.session(), sample(at, "FPS", "60"), sample(at.plusSeconds(1), "FPS", "61")));
        pushOk(other, v2(other.session(), sample(at, "FPS", "60")));

        send(delete(SESSIONS + "/" + other.session() + "/samples").header("Authorization", userBearer(rig.account())),
                404);
        assertThat(rows(other.session())).isEqualTo(1);

        send(delete(SESSIONS + "/" + rig.session() + "/samples").header("Authorization", userBearer(rig.account())), 204);
        assertThat(rows(rig.session())).isZero();
        assertThat(counter(rig.session())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM performance_sessions WHERE id=?", Integer.class,
                rig.session())).isEqualTo(1);
        send(delete(SESSIONS + "/" + rig.session() + "/samples").header("Authorization", userBearer(rig.account())), 204);
        send(delete(SESSIONS + "/" + UUID.randomUUID() + "/samples").header("Authorization",
                userBearer(rig.account())), 404);
        // Silinen aralık yeniden gönderilebilir (makbuz değil, satır benzersizliği geçerlidir).
        assertThat(pushOk(rig, v2(rig.session(), sample(at, "FPS", "60"))).get("storedSamples").asInt()).isEqualTo(1);
    }

    @Test
    void deletingASessionRemovesItsSamplesAndAccountErasureRemovesEverything() throws Exception {
        var rig = rig();
        Instant at = base();
        pushOk(rig, v2(rig.session(), sample(at, "FPS", "60"), sample(at, "CPU_TEMP_C", "60")));
        var second = new Rig(rig.account(), rig.device(), rig.link(), rig.session());
        send(post(SESSIONS + "/" + rig.session() + "/end").header("Authorization", userBearer(rig.account())), 200);
        UUID other = startSession(rig.account());
        pushOk(new Rig(rig.account(), rig.device(), rig.link(), other), v2(other, sample(at, "FPS", "70")));
        assertThat(second.session()).isNotEqualTo(other);

        send(delete(SESSIONS + "/" + rig.session()).header("Authorization", userBearer(rig.account())), 204);
        assertThat(rows(rig.session())).isZero();
        assertThat(rows(other)).isEqualTo(1);

        String erase = PerformanceTestAuth.AUTH.serviceToken("performance-service", "account:erase", rig.account());
        send(delete("/internal/v1/accounts/" + rig.account()).header("Authorization", "Bearer " + erase), 204);
        for (String table : List.of("performance_samples", "performance_sessions", "sync_batches", "device_links",
                "sync_consents")) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE user_id=?", Integer.class,
                    rig.account())).as(table).isZero();
        }
    }

    // ---------- veritabanı kısıtları ----------

    @Test
    void databaseConstraintsBackTheApplicationRules() throws Exception {
        var rig = rig();
        UUID session = rig.session();
        UUID account = rig.account();
        UUID link = rig.link();
        String insert = "INSERT INTO performance_samples(id,session_id,user_id,device_link_id,batch_id,metric,value,"
                + "captured_at,received_at,source) VALUES (gen_random_uuid(),?,?,?,gen_random_uuid(),?,?,"
                + "now(),now(),?)";
        Object[][] invalid = {{"FPS", 0.0, "DEVICE"}, {"FPS", -1.0, "DEVICE"}, {"CPU_TEMP_C", 151.0, "DEVICE"},
            {"CPU_USAGE_PERCENT", 100.5, "DEVICE"}, {"FAN_RPM", -1.0, "DEVICE"}, {"RAM_USED_MB", -2.0, "DEVICE"},
            {"NOT_A_METRIC", 1.0, "DEVICE"}, {"FPS", 60.0, "MANUAL"}, {"FPS", Double.NaN, "DEVICE"}};
        for (Object[] row : invalid) {
            assertThatThrownBy(() -> jdbc.update(insert, session, account, link, row[0], row[1], row[2]))
                    .as(row[0] + "=" + row[1] + "/" + row[2]).isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThatThrownBy(() -> jdbc.update("INSERT INTO performance_samples(id,session_id,user_id,device_link_id,"
                + "batch_id,metric,value,captured_at,received_at,source) VALUES (gen_random_uuid(),?,?,?,"
                + "gen_random_uuid(),'FPS',60,now()+interval '1 hour',now(),'DEVICE')",
                session, account, link)).isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update(insert, session, account, link, "FPS", 60.0, "DEVICE");
        assertThat(rows(session)).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO performance_samples(id,session_id,user_id,device_link_id,"
                + "batch_id,metric,value,captured_at,received_at,source) SELECT gen_random_uuid(),session_id,user_id,"
                + "device_link_id,batch_id,metric,61,captured_at,received_at,source FROM performance_samples "
                + "WHERE session_id=?", session)).isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbc.update("UPDATE performance_sessions SET sample_count=100001 WHERE id=?",
                session)).isInstanceOf(DataIntegrityViolationException.class);
        String receipt = "INSERT INTO sync_batches(id,device_link_id,user_id,idempotency_key,request_hash,"
                + "schema_version,sample_count,client_sent_at,received_at,expires_at,session_id,stored_count,"
                + "duplicate_count) VALUES (gen_random_uuid(),?,?,gen_random_uuid(),repeat('a',64),?,?,now(),now(),"
                + "now()+interval '1 hour',?,?,?)";
        assertThatThrownBy(() -> jdbc.update(receipt, link, account, 2, 3, null, 0, 0))
                .as("v2 makbuzu oturumsuz olamaz").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(receipt, link, account, 1, 3, session, 0, 0))
                .as("v1 makbuzu oturum taşıyamaz").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(receipt, link, account, 2, 3, session, 3, 1))
                .as("yazılan + yinelenen gönderilenden fazla olamaz").isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update(receipt, link, account, 2, 3, session, 2, 1);
    }

    @Test
    void deviceLinkWithSamplesCannotBeHardDeletedWhileSessionsReferenceIt() throws Exception {
        var rig = rig();
        UUID session = rig.session();
        UUID link = rig.link();
        pushOk(rig, v2(session, sample(base(), "FPS", "60")));
        assertThatThrownBy(() -> jdbc.update("DELETE FROM device_links WHERE id=?", link))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(rows(session)).isEqualTo(1);
    }
}

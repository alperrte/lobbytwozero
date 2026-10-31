package com.ltz.performance.api.controller;

import com.ltz.performance.infrastructure.client.PerformancePeerDoubles;
import com.ltz.performance.infrastructure.persistence.PostgresIntegrationSupport;
import com.ltz.performance.infrastructure.security.PerformanceTestAuth;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * PERF-14/15/16/19/21: geçmiş, filtre, istatistik, grafik serisi ve Hardware/Recommendation sözleşme uçları.
 * Gerçek Auth issuer/JWKS (üretim kaynaklarından derlenmiş), Performance HTTP güvenliği ve geçici PostgreSQL kullanılır.
 * Hardware/Recommendation servis token'ları YEREL İMZALI SÖZLEŞME token'larıdır: gerçek Auth servis token'ı bugün yalnız
 * sub=svc:auth-service üretir (REQ-01-C). Bu test gerçek Hardware/Recommendation/Auth entegrasyonu DEĞİLDİR.
 */
@SpringBootTest(properties = {"logging.level.root=INFO", "debug=false"})
@AutoConfigureMockMvc
class PerformanceHistoryIntegrationTest extends PostgresIntegrationSupport {
    static final String CONSENT = "/api/v1/performance/sync-consent";
    static final String DEVICES = "/api/v1/performance/devices";
    static final String SESSIONS = "/api/v1/performance/sessions";
    static final String STATS = "/api/v1/performance/stats";
    static final String INTERNAL = "/internal/v1/accounts/";
    static final String HARDWARE = "svc:hardware-service";
    static final String RECOMMENDATION = "svc:recommendation-service";
    static final String SUMMARY_SCOPE = "summary:read";
    static final String REFERENCE_SCOPE = "reference:read";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    @AfterEach
    void resetPeers() { PerformancePeerDoubles.resetBehaviour(); }

    /** Hesap, onaylı bağlı cihaz bağı ve (ACTIVE oturum yaratmadan) örnek yazılabilir bağ kimliği. */
    record Account(UUID id, UUID link) { }

    // ---------- yardımcılar ----------

    static String bearer(UUID account) throws Exception {
        return "Bearer " + PerformanceTestAuth.AUTH.token(account, "PLAYER");
    }

    JsonNode send(MockHttpServletRequestBuilder request, int expected) throws Exception {
        var response = mvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(expected);
        var body = response.getContentAsString();
        return body.isBlank() ? null : json.readTree(body);
    }

    JsonNode read(Account account, String path, int expected) throws Exception {
        return send(get(path).header("Authorization", bearer(account.id())), expected);
    }

    Account account() throws Exception {
        UUID id = UUID.randomUUID();
        UUID device = UUID.randomUUID();
        PerformanceTestAuth.authDevices(id, device, "Masaüstü PC");
        send(put(CONSENT).header("Authorization", bearer(id)), 200);
        var linked = send(post(DEVICES).header("Authorization", bearer(id)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"deviceId\":\"" + device + "\"}"), 200);
        return new Account(id, UUID.fromString(linked.get("id").asString()));
    }

    UUID session(Account account, UUID game, UUID hardware, String status, Instant startedAt) {
        UUID id = UUID.randomUUID();
        Instant ended = "ACTIVE".equals(status) ? null : startedAt.plusSeconds(600);
        jdbc.update("""
                INSERT INTO performance_sessions(id,user_id,device_link_id,game_id,game_title,hardware_profile_id,
                    source,status,started_at,ended_at,version,created_at,updated_at)
                VALUES (?,?,?,?,?,?,'MANUAL',?,?,?,0,now(),now())
                """, id, account.id(), account.link(), game, "Oyun", hardware, status, Timestamp.from(startedAt),
                ended == null ? null : Timestamp.from(ended));
        return id;
    }

    UUID completed(Account account, UUID game, UUID hardware, Instant startedAt) {
        return session(account, game, hardware, "COMPLETED", startedAt);
    }

    void sample(Account account, UUID session, String metric, Instant at, double value) {
        jdbc.update("""
                INSERT INTO performance_samples(id,session_id,user_id,device_link_id,batch_id,metric,value,captured_at,
                    received_at,source) VALUES (gen_random_uuid(),?,?,?,gen_random_uuid(),?,?,?,?,'DEVICE')
                """, session, account.id(), account.link(), metric, value, Timestamp.from(at), Timestamp.from(at));
        recount(session);
    }

    /** {@code count} adet saniyelik örnek; değer 30 + (i mod 100) olduğundan ortalama/min/max bilinir. */
    void series(Account account, UUID session, String metric, Instant start, int count, long stepMillis) {
        jdbc.update("""
                INSERT INTO performance_samples(id,session_id,user_id,device_link_id,batch_id,metric,value,captured_at,
                    received_at,source)
                SELECT gen_random_uuid(),?,?,?,gen_random_uuid(),?,30 + (g % 50),
                    ?::timestamptz + g * ? * interval '1 millisecond', ?::timestamptz + g * ? * interval '1 millisecond','DEVICE'
                FROM generate_series(0, ? - 1) AS g
                """, session, account.id(), account.link(), metric, Timestamp.from(start), stepMillis,
                Timestamp.from(start), stepMillis, count);
        recount(session);
    }

    void recount(UUID session) {
        jdbc.update("UPDATE performance_sessions SET sample_count=(SELECT count(*) FROM performance_samples "
                + "WHERE session_id=?) WHERE id=?", session, session);
    }

    static Instant now() { return Instant.now().truncatedTo(ChronoUnit.MILLIS); }

    static String serviceToken(String subject, String scope, UUID account, String audience) throws Exception {
        Instant now = Instant.now();
        return PerformanceTestAuth.sign(PerformanceTestAuth.AUTH.key(), new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(PerformanceTestAuth.AUTH.key().getKeyID()).type(new JOSEObjectType("svc+jwt")).build(),
                Map.of("iss", "ltz-auth", "sub", subject, "aud", List.of(audience), "scope", scope,
                        "account_id", account.toString(), "jti", UUID.randomUUID().toString(),
                        "iat", now.getEpochSecond(), "nbf", now.getEpochSecond(),
                        "exp", now.plusSeconds(30).getEpochSecond()));
    }

    JsonNode internal(String path, String subject, String scope, UUID tokenAccount, int expected) throws Exception {
        return send(get(path).header("Authorization", "Bearer "
                + serviceToken(subject, scope, tokenAccount, "performance-service")), expected);
    }

    static List<String> ids(JsonNode page) {
        var ids = new ArrayList<String>();
        for (JsonNode item : page.get("items")) ids.add(item.get("id").asString());
        return ids;
    }

    static Set<String> names(JsonNode node) { return new TreeSet<>(node.propertyNames()); }

    // ---------- PERF-14/19: oturum geçmişi ----------

    @Test
    void historyFiltersCombineAndOrderIsDeterministic() throws Exception {
        var account = account();
        UUID gameA = UUID.randomUUID();
        UUID gameB = UUID.randomUUID();
        UUID hardware = UUID.randomUUID();
        Instant t0 = now().minus(10, ChronoUnit.DAYS);
        UUID s1 = completed(account, gameA, hardware, t0);
        UUID s2 = completed(account, gameA, null, t0.plus(1, ChronoUnit.DAYS));
        UUID s3 = session(account, gameB, hardware, "ABANDONED", t0.plus(2, ChronoUnit.DAYS));
        UUID s4 = completed(account, gameB, hardware, t0.plus(3, ChronoUnit.DAYS));

        assertThat(ids(read(account, SESSIONS, 200)))
                .containsExactly(s4.toString(), s3.toString(), s2.toString(), s1.toString());
        assertThat(ids(read(account, SESSIONS + "?gameId=" + gameA, 200))).containsExactly(s2.toString(), s1.toString());
        assertThat(ids(read(account, SESSIONS + "?hardwareProfileId=" + hardware, 200)))
                .containsExactly(s4.toString(), s3.toString(), s1.toString());
        assertThat(ids(read(account, SESSIONS + "?status=ABANDONED", 200))).containsExactly(s3.toString());
        assertThat(ids(read(account, SESSIONS + "?deviceLinkId=" + account.link() + "&gameId=" + gameB, 200)))
                .containsExactly(s4.toString(), s3.toString());
        assertThat(ids(read(account, SESSIONS + "?gameId=" + gameB + "&status=COMPLETED&hardwareProfileId=" + hardware,
                200))).containsExactly(s4.toString());
        assertThat(ids(read(account, SESSIONS + "?deviceLinkId=" + UUID.randomUUID(), 200))).isEmpty();
    }

    @Test
    void startedRangeIsHalfOpenAndInclusiveAtTheLowerBound() throws Exception {
        var account = account();
        UUID game = UUID.randomUUID();
        Instant t0 = now().minus(5, ChronoUnit.DAYS);
        UUID early = completed(account, game, null, t0);
        UUID middle = completed(account, game, null, t0.plusSeconds(3600));
        UUID late = completed(account, game, null, t0.plusSeconds(7200));

        var range = SESSIONS + "?startedFrom=" + t0.plusSeconds(3600) + "&startedTo=" + t0.plusSeconds(7200);
        assertThat(ids(read(account, range, 200))).containsExactly(middle.toString());
        assertThat(ids(read(account, SESSIONS + "?startedFrom=" + t0.plusSeconds(3600), 200)))
                .containsExactly(late.toString(), middle.toString());
        assertThat(ids(read(account, SESSIONS + "?startedTo=" + t0.plusSeconds(3600), 200)))
                .containsExactly(early.toString());
        assertThat(ids(read(account, SESSIONS + "?startedFrom=" + t0.plusSeconds(9000), 200))).isEmpty();
    }

    @Test
    void paginationCoversEveryRowOnceWithStableTieBreak() throws Exception {
        var account = account();
        UUID game = UUID.randomUUID();
        Instant same = now().minus(2, ChronoUnit.DAYS);
        var created = new ArrayList<String>();
        for (int i = 0; i < 7; i++) created.add(completed(account, game, null, same).toString());

        var seen = new ArrayList<String>();
        for (int page = 0; page < 4; page++) {
            var body = read(account, SESSIONS + "?size=2&page=" + page, 200);
            assertThat(body.get("totalElements").asLong()).isEqualTo(7);
            assertThat(body.get("hasNext").asBoolean()).isEqualTo(page < 3);
            seen.addAll(ids(body));
        }
        assertThat(seen).hasSize(7).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(created);
        var expected = new ArrayList<>(created);
        expected.sort(java.util.Comparator.reverseOrder());
        assertThat(seen).isEqualTo(expected);
        var beyond = read(account, SESSIONS + "?size=2&page=99", 200);
        assertThat(beyond.get("items")).isEmpty();
        assertThat(beyond.get("hasNext").asBoolean()).isFalse();
    }

    @Test
    void emptyHistoryAndInvalidInputs() throws Exception {
        var account = account();
        var empty = read(account, SESSIONS, 200);
        assertThat(empty.get("items")).isEmpty();
        assertThat(empty.get("totalElements").asLong()).isZero();
        Instant a = now();
        for (String bad : List.of("size=0", "size=51", "size=-1", "page=-1", "status=BOGUS", "gameId=not-a-uuid",
                "startedFrom=2024-13-45", "startedFrom=" + a + "&startedTo=" + a,
                "startedFrom=" + a + "&startedTo=" + a.minusSeconds(1), "startedFrom=1969-01-01T00:00:00Z",
                "startedTo=+999999999-12-31T23:59:59Z")) {
            var problem = read(account, SESSIONS + "?" + bad, 400);
            assertThat(problem.get("code").asString()).as(bad).isEqualTo("INVALID_REQUEST");
        }
    }

    @Test
    void historyNeverContainsAnotherAccountsSessions() throws Exception {
        var mine = account();
        var theirs = account();
        UUID sharedGame = UUID.randomUUID();
        completed(theirs, sharedGame, null, now().minusSeconds(3600));
        UUID own = completed(mine, sharedGame, null, now().minusSeconds(7200));

        assertThat(ids(read(mine, SESSIONS + "?gameId=" + sharedGame, 200))).containsExactly(own.toString());
        assertThat(read(mine, SESSIONS + "?gameId=" + sharedGame, 200).get("totalElements").asLong()).isEqualTo(1);
    }

    // ---------- PERF-14: ham örnekler ----------

    @Test
    void samplePagesHaveFixedOrderFiltersAndNoInternalIdentifiers() throws Exception {
        var account = account();
        UUID session = completed(account, UUID.randomUUID(), null, now().minusSeconds(600));
        Instant t = now().minusSeconds(300);
        sample(account, session, "FPS", t.plusSeconds(2), 61);
        sample(account, session, "CPU_TEMP_C", t.plusSeconds(1), 70);
        sample(account, session, "FPS", t.plusSeconds(1), 60);
        sample(account, session, "GPU_TEMP_C", t.plusSeconds(1), 65);
        sample(account, session, "FPS", t.plusSeconds(3), 62);
        String path = SESSIONS + "/" + session + "/samples";

        var all = read(account, path, 200);
        var order = new ArrayList<String>();
        for (JsonNode item : all.get("items")) order.add(item.get("metric").asString() + "@" + item.get("capturedAt").asString());
        assertThat(order).containsExactly("CPU_TEMP_C@" + t.plusSeconds(1), "FPS@" + t.plusSeconds(1),
                "GPU_TEMP_C@" + t.plusSeconds(1), "FPS@" + t.plusSeconds(2), "FPS@" + t.plusSeconds(3));
        assertThat(names(all.get("items").get(0))).containsExactly("capturedAt", "metric", "unit", "value");
        assertThat(all.get("items").get(0).get("unit").asString()).isEqualTo("°C");

        var firstPage = read(account, path + "?size=2", 200);
        assertThat(firstPage.get("hasNext").asBoolean()).isTrue();
        assertThat(firstPage.get("items")).hasSize(2);
        assertThat(read(account, path + "?size=2&page=2", 200).get("hasNext").asBoolean()).isFalse();
        assertThat(read(account, path + "?size=2&page=2", 200).get("items")).hasSize(1);

        var fps = read(account, path + "?metric=FPS", 200);
        assertThat(fps.get("items")).hasSize(3);
        var window = read(account, path + "?metric=FPS&from=" + t.plusSeconds(2) + "&to=" + t.plusSeconds(3), 200);
        assertThat(window.get("items")).hasSize(1);
        assertThat(window.get("items").get(0).get("value").asDouble()).isEqualTo(61.0);
    }

    @Test
    void sampleReadsRejectInvalidParameters() throws Exception {
        var account = account();
        UUID session = completed(account, UUID.randomUUID(), null, now().minusSeconds(600));
        String path = SESSIONS + "/" + session + "/samples";
        for (String bad : List.of("size=0", "size=501", "page=-1", "metric=fps", "metric=CPU", "metric=",
                "from=" + now() + "&to=" + now().minusSeconds(5))) {
            assertThat(read(account, path + "?" + bad, 400).get("code").asString()).as(bad).isEqualTo("INVALID_REQUEST");
        }
        assertThat(read(account, path + "?size=500", 200).get("items")).isEmpty();
    }

    // ---------- IDOR ----------

    @Test
    void anotherAccountsOrMissingSessionIsTheSame404ForEveryRead() throws Exception {
        var owner = account();
        var intruder = account();
        UUID session = completed(owner, UUID.randomUUID(), null, now().minusSeconds(600));
        sample(owner, session, "FPS", now().minusSeconds(300), 60);
        for (String suffix : List.of("/samples", "/stats", "/series?metric=FPS")) {
            var foreign = read(intruder, SESSIONS + "/" + session + suffix, 404);
            var missing = read(intruder, SESSIONS + "/" + UUID.randomUUID() + suffix, 404);
            assertThat(foreign.get("code").asString()).isEqualTo("PERFORMANCE_NOT_FOUND");
            assertThat(foreign.get("detail")).isEqualTo(missing.get("detail"));
            assertThat(foreign.get("status")).isEqualTo(missing.get("status"));
            assertThat(foreign.get("code")).isEqualTo(missing.get("code"));
        }
        assertThat(read(owner, SESSIONS + "/" + session + "/samples", 200).get("items")).hasSize(1);
    }

    @Test
    void historyEndpointsRequireAUserToken() throws Exception {
        var account = account();
        UUID session = completed(account, UUID.randomUUID(), null, now().minusSeconds(600));
        String deviceToken = PerformanceTestAuth.performanceDeviceToken(UUID.randomUUID(), account.id());
        String serviceToken = serviceToken(HARDWARE, SUMMARY_SCOPE, account.id(), "performance-service");
        for (String path : List.of(SESSIONS, SESSIONS + "/" + session + "/samples", SESSIONS + "/" + session + "/stats",
                SESSIONS + "/" + session + "/series?metric=FPS", STATS)) {
            mvcStatus(get(path), 401);
            mvcStatus(get(path).header("Authorization", "Bearer " + deviceToken), 401);
            mvcStatus(get(path).header("Authorization", "Bearer " + serviceToken), 401);
            mvcStatus(get(path).header("Authorization", "Bearer garbage"), 401);
        }
    }

    void mvcStatus(MockHttpServletRequestBuilder request, int expected) throws Exception {
        assertThat(mvc.perform(request).andReturn().getResponse().getStatus()).isEqualTo(expected);
    }

    // ---------- PERF-15: istatistik ----------

    @Test
    void sessionStatsAreExactOverRealSamplesAndOmitUnmeasuredMetrics() throws Exception {
        var account = account();
        UUID session = completed(account, UUID.randomUUID(), null, now().minusSeconds(600));
        Instant t = now().minusSeconds(300);
        sample(account, session, "FPS", t, 30);
        sample(account, session, "FPS", t.plusSeconds(1), 60);
        sample(account, session, "FPS", t.plusSeconds(2), 90);
        sample(account, session, "CPU_TEMP_C", t, 70.5);
        sample(account, session, "CPU_TEMP_C", t.plusSeconds(1), 80.5);

        var stats = read(account, SESSIONS + "/" + session + "/stats", 200);
        assertThat(stats.get("sessionId").asString()).isEqualTo(session.toString());
        assertThat(stats.get("sampleCount").asLong()).isEqualTo(5);
        assertThat(stats.get("metrics")).hasSize(2);
        var cpu = stats.get("metrics").get(0);
        var fps = stats.get("metrics").get(1);
        assertThat(cpu.get("metric").asString()).isEqualTo("CPU_TEMP_C");
        assertThat(cpu.get("unit").asString()).isEqualTo("°C");
        assertThat(cpu.get("count").asLong()).isEqualTo(2);
        assertThat(cpu.get("avg").asDouble()).isEqualTo(75.5);
        assertThat(cpu.get("min").asDouble()).isEqualTo(70.5);
        assertThat(cpu.get("max").asDouble()).isEqualTo(80.5);
        assertThat(fps.get("metric").asString()).isEqualTo("FPS");
        assertThat(fps.get("unit").asString()).isEqualTo("FPS");
        assertThat(fps.get("count").asLong()).isEqualTo(3);
        assertThat(fps.get("avg").asDouble()).isEqualTo(60.0);
        assertThat(fps.get("min").asDouble()).isEqualTo(30.0);
        assertThat(fps.get("max").asDouble()).isEqualTo(90.0);
        assertThat(fps.get("sessionCount").asLong()).isEqualTo(1);
        assertThat(Instant.parse(fps.get("firstAt").asString())).isEqualTo(t);
        assertThat(Instant.parse(fps.get("lastAt").asString())).isEqualTo(t.plusSeconds(2));

    }

    @Test
    void sessionStatsHonorMetricAndTimeWindowFilters() throws Exception {
        var account = account();
        UUID session = completed(account, UUID.randomUUID(), null, now().minusSeconds(600));
        Instant t = now().minusSeconds(300);
        sample(account, session, "FPS", t, 30);
        sample(account, session, "FPS", t.plusSeconds(1), 60);
        sample(account, session, "FPS", t.plusSeconds(2), 90);
        sample(account, session, "CPU_TEMP_C", t, 70.5);

        var onlyFps = read(account, SESSIONS + "/" + session + "/stats?metric=FPS", 200);
        assertThat(onlyFps.get("metrics")).hasSize(1);
        var windowed = read(account, SESSIONS + "/" + session + "/stats?metric=FPS&from=" + t.plusSeconds(1), 200);
        assertThat(windowed.get("metrics").get(0).get("count").asLong()).isEqualTo(2);
        assertThat(windowed.get("metrics").get(0).get("avg").asDouble()).isEqualTo(75.0);
        assertThat(windowed.get("metrics").get(0).get("min").asDouble()).isEqualTo(60.0);
    }

    @Test
    void statsAgreeWithSampleListingAndSessionCounter() throws Exception {
        var account = account();
        UUID session = completed(account, UUID.randomUUID(), null, now().minusSeconds(3600));
        series(account, session, "FPS", now().minusSeconds(1800), 250, 1000);
        series(account, session, "GPU_USAGE_PERCENT", now().minusSeconds(1800), 100, 1000);

        var stats = read(account, SESSIONS + "/" + session + "/stats", 200);
        long total = 0;
        for (JsonNode metric : stats.get("metrics")) total += metric.get("count").asLong();
        assertThat(total).isEqualTo(350);
        assertThat(stats.get("sampleCount").asLong()).isEqualTo(350);
        var detail = read(account, SESSIONS + "/" + session, 200);
        assertThat(detail.get("sampleCount").asInt()).isEqualTo(350);
        long listed = 0;
        for (int page = 0; page < 4; page++) {
            listed += read(account, SESSIONS + "/" + session + "/samples?size=100&page=" + page, 200).get("items").size();
        }
        assertThat(listed).isEqualTo(350);
        Double expectedAvg = jdbc.queryForObject("SELECT avg(value) FROM performance_samples WHERE session_id=? "
                + "AND metric='FPS'", Double.class, session);
        assertThat(stats.get("metrics").get(0).get("avg").asDouble()).isEqualTo(expectedAvg);
    }

    @Test
    void emptySessionHasNoMetricsInsteadOfZeros() throws Exception {
        var account = account();
        UUID session = completed(account, UUID.randomUUID(), null, now().minusSeconds(600));
        var stats = read(account, SESSIONS + "/" + session + "/stats", 200);
        assertThat(stats.get("sampleCount").asLong()).isZero();
        assertThat(stats.get("metrics")).isEmpty();
        var series = read(account, SESSIONS + "/" + session + "/series?metric=FPS", 200);
        assertThat(series.get("points")).isEmpty();
        assertThat(series.get("totalSamples").asLong()).isZero();
        assertThat(series.get("downsampled").asBoolean()).isFalse();
    }

    @Test
    void accountStatsAggregateAcrossFilteredSessionsWithSampleWeighting() throws Exception {
        var account = account();
        var other = account();
        UUID game = UUID.randomUUID();
        UUID otherGame = UUID.randomUUID();
        Instant t0 = now().minus(3, ChronoUnit.DAYS);
        UUID s1 = completed(account, game, null, t0);
        UUID s2 = completed(account, game, null, t0.plusSeconds(3600));
        UUID s3 = completed(account, otherGame, null, t0.plusSeconds(7200));
        UUID empty = completed(account, game, null, t0.plusSeconds(9000));
        sample(account, s1, "FPS", t0.plusSeconds(10), 40);
        sample(account, s2, "FPS", t0.plusSeconds(3610), 100);
        sample(account, s2, "FPS", t0.plusSeconds(3611), 100);
        sample(account, s2, "CPU_USAGE_PERCENT", t0.plusSeconds(3611), 50);
        sample(account, s3, "FPS", t0.plusSeconds(7210), 1000);
        UUID foreign = completed(other, game, null, t0);
        sample(other, foreign, "FPS", t0.plusSeconds(5), 999);

        var byGame = read(account, STATS + "?gameId=" + game, 200);
        assertThat(byGame.get("sessionCount").asLong()).isEqualTo(3);
        assertThat(byGame.get("sampleCount").asLong()).isEqualTo(4);
        var cpu = byGame.get("metrics").get(0);
        var fps = byGame.get("metrics").get(1);
        assertThat(cpu.get("metric").asString()).isEqualTo("CPU_USAGE_PERCENT");
        assertThat(cpu.get("count").asLong()).isEqualTo(1);
        assertThat(fps.get("count").asLong()).isEqualTo(3);
        assertThat(fps.get("sessionCount").asLong()).isEqualTo(2);
        assertThat(fps.get("avg").asDouble()).isEqualTo(80.0);
        assertThat(fps.get("min").asDouble()).isEqualTo(40.0);
        assertThat(fps.get("max").asDouble()).isEqualTo(100.0);
        assertThat(empty).isNotNull();

        var everything = read(account, STATS + "?metric=FPS", 200);
        assertThat(everything.get("metrics")).hasSize(1);
        assertThat(everything.get("metrics").get(0).get("max").asDouble()).isEqualTo(1000.0);
        var ranged = read(account, STATS + "?metric=FPS&startedFrom=" + t0.plusSeconds(3000) + "&startedTo="
                + t0.plusSeconds(8000), 200);
        assertThat(ranged.get("sessionCount").asLong()).isEqualTo(2);
        assertThat(ranged.get("metrics").get(0).get("count").asLong()).isEqualTo(3);
        var none = read(account, STATS + "?gameId=" + UUID.randomUUID(), 200);
        assertThat(none.get("sessionCount").asLong()).isZero();
        assertThat(none.get("metrics")).isEmpty();
        assertThat(read(account, STATS + "?metric=NOPE", 400).get("code").asString()).isEqualTo("INVALID_REQUEST");
    }

    // ---------- PERF-16: grafik serisi ----------

    @Test
    void smallSeriesReturnsRawPointsInTimeOrder() throws Exception {
        var account = account();
        UUID session = completed(account, UUID.randomUUID(), null, now().minusSeconds(600));
        Instant t = now().minusSeconds(300);
        sample(account, session, "FPS", t.plusSeconds(2), 62);
        sample(account, session, "FPS", t, 60);
        sample(account, session, "FPS", t.plusSeconds(1), 61);
        sample(account, session, "CPU_TEMP_C", t, 70);

        var series = read(account, SESSIONS + "/" + session + "/series?metric=FPS", 200);
        assertThat(series.get("resolution").asString()).isEqualTo("RAW");
        assertThat(series.get("downsampled").asBoolean()).isFalse();
        assertThat(series.get("aggregation").asString()).isEqualTo("NONE");
        assertThat(series.get("bucketMillis").isNull()).isTrue();
        assertThat(series.get("totalSamples").asLong()).isEqualTo(3);
        assertThat(series.get("unit").asString()).isEqualTo("FPS");
        var points = series.get("points");
        assertThat(points).hasSize(3);
        assertThat(Instant.parse(points.get(0).get("at").asString())).isEqualTo(t);
        assertThat(points.get(0).get("value").asDouble()).isEqualTo(60.0);
        assertThat(points.get(2).get("value").asDouble()).isEqualTo(62.0);
        assertThat(points.get(1).get("count").asLong()).isEqualTo(1);
        assertThat(names(points.get(0))).containsExactly("at", "count", "max", "min", "value");
    }

    @Test
    void largeSeriesIsDownsampledWithinTheRequestedBoundAndKeepsTotals() throws Exception {
        var account = account();
        UUID session = completed(account, UUID.randomUUID(), null, now().minusSeconds(30_000));
        Instant start = now().minusSeconds(25_000);
        series(account, session, "FPS", start, 20_000, 1000);

        var series = read(account, SESSIONS + "/" + session + "/series?metric=FPS&maxPoints=100", 200);
        assertThat(series.get("resolution").asString()).isEqualTo("BUCKET");
        assertThat(series.get("downsampled").asBoolean()).isTrue();
        assertThat(series.get("aggregation").asString()).isEqualTo("AVG_MIN_MAX");
        assertThat(series.get("totalSamples").asLong()).isEqualTo(20_000);
        long bucket = series.get("bucketMillis").asLong();
        assertThat(bucket).isEqualTo(199_991L);
        var points = series.get("points");
        assertThat(points.size()).isBetween(1, 100);
        long count = 0;
        double weighted = 0;
        double min = Double.MAX_VALUE;
        double max = 0;
        Instant previous = null;
        for (JsonNode point : points) {
            Instant at = Instant.parse(point.get("at").asString());
            if (previous != null) assertThat(at).isAfter(previous);
            assertThat(at.toEpochMilli() - start.toEpochMilli()).isNotNegative().isEqualTo(
                    (at.toEpochMilli() - start.toEpochMilli()) / bucket * bucket);
            previous = at;
            long n = point.get("count").asLong();
            count += n;
            weighted += point.get("value").asDouble() * n;
            min = Math.min(min, point.get("min").asDouble());
            max = Math.max(max, point.get("max").asDouble());
            assertThat(point.get("min").asDouble()).isLessThanOrEqualTo(point.get("value").asDouble());
            assertThat(point.get("max").asDouble()).isGreaterThanOrEqualTo(point.get("value").asDouble());
        }
        assertThat(count).isEqualTo(20_000);
        assertThat(weighted / count).isCloseTo(54.5, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(min).isEqualTo(30.0);
        assertThat(max).isEqualTo(79.0);
    }

    @Test
    void seriesWindowAndMaxPointsBoundTheResponseForLongRanges() throws Exception {
        var account = account();
        UUID session = completed(account, UUID.randomUUID(), null, now().minus(3, ChronoUnit.DAYS));
        Instant start = now().minus(2, ChronoUnit.DAYS);
        series(account, session, "GPU_TEMP_C", start, 1500, 120_000);

        String base = SESSIONS + "/" + session + "/series?metric=GPU_TEMP_C";
        var coarse = read(account, base + "&maxPoints=10", 200);
        assertThat(coarse.get("points").size()).isBetween(1, 10);
        assertThat(coarse.get("totalSamples").asLong()).isEqualTo(1500);
        var narrowed = read(account, base + "&from=" + start.plusSeconds(120 * 100) + "&to=" + start.plusSeconds(120 * 110)
                + "&maxPoints=1000", 200);
        assertThat(narrowed.get("resolution").asString()).isEqualTo("RAW");
        assertThat(narrowed.get("totalSamples").asLong()).isEqualTo(10);
        assertThat(narrowed.get("points")).hasSize(10);
        var outside = read(account, base + "&from=" + start.plus(10, ChronoUnit.DAYS), 200);
        assertThat(outside.get("points")).isEmpty();
        var exactlyAtBound = read(account, base + "&maxPoints=1000&from=" + start.plusSeconds(120 * 1499), 200);
        assertThat(exactlyAtBound.get("totalSamples").asLong()).isEqualTo(1);
    }

    @Test
    void seriesRejectsInvalidRequests() throws Exception {
        var account = account();
        UUID session = completed(account, UUID.randomUUID(), null, now().minusSeconds(600));
        String path = SESSIONS + "/" + session + "/series";
        for (String bad : List.of("", "?metric=BOGUS", "?metric=FPS&maxPoints=9", "?metric=FPS&maxPoints=1001",
                "?metric=FPS&maxPoints=abc", "?metric=FPS&from=" + now() + "&to=" + now().minusSeconds(1))) {
            assertThat(read(account, path + bad, 400).get("code").asString()).as(bad).isEqualTo("INVALID_REQUEST");
        }
    }

    @Test
    void sampleQueriesUseTheSessionMetricTimeIndex() throws Exception {
        var account = account();
        UUID session = completed(account, UUID.randomUUID(), null, now().minusSeconds(30_000));
        series(account, session, "FPS", now().minusSeconds(25_000), 5000, 1000);
        jdbc.execute("ANALYZE performance_samples");
        String plan = jdbc.execute((ConnectionCallback<String>) connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("SET enable_seqscan = off");
                var builder = new StringBuilder();
                try (var rows = statement.executeQuery("EXPLAIN SELECT captured_at, value FROM performance_samples "
                        + "WHERE session_id='" + session + "' AND user_id='" + account.id() + "' AND metric='FPS' "
                        + "AND captured_at>=now() - interval '1 day' ORDER BY captured_at")) {
                    while (rows.next()) builder.append(rows.getString(1)).append('\n');
                }
                statement.execute("RESET enable_seqscan");
                return builder.toString();
            }
        });
        assertThat(plan).contains("uq_performance_samples_point").doesNotContain("Seq Scan");
    }

    // ---------- PERF-21: Hardware / Recommendation sözleşmeleri ----------

    @Test
    void hardwareSummaryCoversOnlyCompletedSessionsOfTheProfileAndExposesNoIdentifiers() throws Exception {
        var account = account();
        var other = account();
        UUID hardware = UUID.randomUUID();
        UUID game = UUID.randomUUID();
        Instant t0 = now().minus(2, ChronoUnit.DAYS);
        UUID done1 = completed(account, game, hardware, t0);
        UUID done2 = completed(account, UUID.randomUUID(), hardware, t0.plusSeconds(3600));
        UUID abandoned = session(account, game, hardware, "ABANDONED", t0.plusSeconds(7200));
        UUID otherProfile = completed(account, game, UUID.randomUUID(), t0.plusSeconds(9000));
        sample(account, done1, "FPS", t0.plusSeconds(10), 50);
        sample(account, done2, "FPS", t0.plusSeconds(3610), 70);
        sample(account, done2, "GPU_TEMP_C", t0.plusSeconds(3610), 75);
        sample(account, abandoned, "FPS", t0.plusSeconds(7210), 1);
        sample(account, otherProfile, "FPS", t0.plusSeconds(9010), 999);
        UUID foreign = completed(other, game, hardware, t0);
        sample(other, foreign, "FPS", t0.plusSeconds(5), 888);

        var summary = internal(INTERNAL + account.id() + "/performance-summary?hardwareProfileId=" + hardware, HARDWARE,
                SUMMARY_SCOPE, account.id(), 200);
        assertThat(names(summary)).containsExactly("hardwareProfileId", "lastMeasuredAt", "metrics", "sampleCount",
                "sessionCount");
        assertThat(summary.get("hardwareProfileId").asString()).isEqualTo(hardware.toString());
        assertThat(summary.get("sessionCount").asLong()).isEqualTo(2);
        assertThat(summary.get("sampleCount").asLong()).isEqualTo(3);
        assertThat(Instant.parse(summary.get("lastMeasuredAt").asString())).isEqualTo(t0.plusSeconds(3610));
        var fps = summary.get("metrics").get(0);
        assertThat(fps.get("metric").asString()).isEqualTo("FPS");
        assertThat(fps.get("avg").asDouble()).isEqualTo(60.0);
        assertThat(fps.get("max").asDouble()).isEqualTo(70.0);
        assertThat(summary.toString()).doesNotContain(done1.toString(), done2.toString(), abandoned.toString())
                .doesNotContain(game.toString(), account.link().toString(), "Oyun");

        var none = internal(INTERNAL + account.id() + "/performance-summary?hardwareProfileId=" + UUID.randomUUID(),
                HARDWARE, SUMMARY_SCOPE, account.id(), 200);
        assertThat(none.get("sessionCount").asLong()).isZero();
        assertThat(none.get("metrics")).isEmpty();
        assertThat(none.get("lastMeasuredAt").isNull()).isTrue();
    }

    @Test
    void gameReferenceIsReliableOnlyWithEnoughCompletedFpsSamples() throws Exception {
        var account = account();
        var other = account();
        UUID game = UUID.randomUUID();
        UUID hardware = UUID.randomUUID();
        Instant t0 = now().minus(2, ChronoUnit.DAYS);
        UUID few = completed(account, game, hardware, t0);
        series(account, few, "FPS", t0.plusSeconds(10), 59, 1000);
        String path = INTERNAL + account.id() + "/games/" + game + "/performance-reference";

        var unreliable = internal(path, RECOMMENDATION, REFERENCE_SCOPE, account.id(), 200);
        assertThat(unreliable.get("reliable").asBoolean()).isFalse();
        assertThat(unreliable.get("fpsSampleCount").asLong()).isEqualTo(59);
        assertThat(unreliable.get("avgFps").isNull()).isTrue();
        assertThat(unreliable.get("minFps").isNull()).isTrue();
        assertThat(unreliable.get("maxFps").isNull()).isTrue();

        UUID more = completed(account, game, hardware, t0.plusSeconds(3600));
        series(account, more, "FPS", t0.plusSeconds(3700), 41, 1000);
        UUID abandoned = session(account, game, hardware, "ABANDONED", t0.plusSeconds(8000));
        series(account, abandoned, "FPS", t0.plusSeconds(8100), 500, 1000);
        sample(account, more, "CPU_TEMP_C", t0.plusSeconds(3700), 99);
        UUID foreign = completed(other, game, hardware, t0);
        series(other, foreign, "FPS", t0.plusSeconds(10), 500, 1000);

        var reliable = internal(path, RECOMMENDATION, REFERENCE_SCOPE, account.id(), 200);
        assertThat(names(reliable)).containsExactly("avgFps", "fpsSampleCount", "gameId", "hardwareProfileId",
                "lastMeasuredAt", "maxFps", "minFps", "reliable", "sessionCount");
        assertThat(reliable.get("reliable").asBoolean()).isTrue();
        assertThat(reliable.get("sessionCount").asLong()).isEqualTo(2);
        assertThat(reliable.get("fpsSampleCount").asLong()).isEqualTo(100);
        assertThat(reliable.get("minFps").asDouble()).isEqualTo(30.0);
        assertThat(reliable.get("maxFps").asDouble()).isEqualTo(79.0);
        Double expected = jdbc.queryForObject("SELECT avg(value) FROM performance_samples WHERE metric='FPS' "
                + "AND session_id IN (?,?)", Double.class, few, more);
        assertThat(reliable.get("avgFps").asDouble()).isEqualTo(expected);
        assertThat(reliable.get("hardwareProfileId").isNull()).isTrue();

        var byHardware = internal(path + "?hardwareProfileId=" + hardware, RECOMMENDATION, REFERENCE_SCOPE,
                account.id(), 200);
        assertThat(byHardware.get("reliable").asBoolean()).isTrue();
        assertThat(byHardware.get("hardwareProfileId").asString()).isEqualTo(hardware.toString());
        var otherHardware = internal(path + "?hardwareProfileId=" + UUID.randomUUID(), RECOMMENDATION, REFERENCE_SCOPE,
                account.id(), 200);
        assertThat(otherHardware.get("reliable").asBoolean()).isFalse();
        assertThat(otherHardware.get("fpsSampleCount").asLong()).isZero();
        assertThat(otherHardware.get("lastMeasuredAt").isNull()).isTrue();
    }

    @Test
    void contractEndpointsRejectWrongOrMissingServiceIdentity() throws Exception {
        var account = account();
        UUID hardware = UUID.randomUUID();
        UUID game = UUID.randomUUID();
        String summary = INTERNAL + account.id() + "/performance-summary?hardwareProfileId=" + hardware;
        String reference = INTERNAL + account.id() + "/games/" + game + "/performance-reference";

        // Yanlış servis konusu (başka bir servisin token'ı diğerinin ucunu çağıramaz)
        internal(summary, RECOMMENDATION, SUMMARY_SCOPE, account.id(), 403);
        internal(reference, HARDWARE, REFERENCE_SCOPE, account.id(), 403);
        // Gerçek Auth servis token'ı yalnız sub=svc:auth-service üretir → iki uç da reddeder (REQ-01-C)
        String real = PerformanceTestAuth.AUTH.serviceToken("performance-service", "devices:verify", account.id());
        mvcStatus(get(summary).header("Authorization", "Bearer " + real), 403);
        mvcStatus(get(reference).header("Authorization", "Bearer " + real), 403);
        // Kapsam doğru olsa bile gerçek Auth token'ının konusu svc:auth-service'tir; Hardware/Recommendation değildir
        String authSummary = PerformanceTestAuth.AUTH.serviceToken("performance-service", SUMMARY_SCOPE, account.id());
        mvcStatus(get(summary).header("Authorization", "Bearer " + authSummary), 403);
        // Yanlış kapsam, diğer ucun kapsamı ve başka hesaba bağlı token
        internal(summary, HARDWARE, REFERENCE_SCOPE, account.id(), 403);
        internal(reference, RECOMMENDATION, SUMMARY_SCOPE, account.id(), 403);
        internal(summary, HARDWARE, "devices:verify", account.id(), 403);
        internal(summary, HARDWARE, SUMMARY_SCOPE, UUID.randomUUID(), 403);
        internal(reference, RECOMMENDATION, REFERENCE_SCOPE, UUID.randomUUID(), 403);
        // Yanlış hedef kitle, kimliksiz, kullanıcı ve cihaz token'ı
        mvcStatus(get(summary).header("Authorization", "Bearer "
                + serviceToken(HARDWARE, SUMMARY_SCOPE, account.id(), "hardware-service")), 401);
        mvcStatus(get(summary), 401);
        mvcStatus(get(reference), 401);
        mvcStatus(get(summary).header("Authorization", bearer(account.id())), 401);
        mvcStatus(get(reference).header("Authorization", "Bearer "
                + PerformanceTestAuth.performanceDeviceToken(UUID.randomUUID(), account.id())), 401);
        // Eksik zorunlu parametre
        internal(INTERNAL + account.id() + "/performance-summary", HARDWARE, SUMMARY_SCOPE, account.id(), 400);
        // Geçerli kimlik yine de başka hesabın verisini görmez (hesap, token'daki account_id ile aynı olmalı)
        var valid = internal(summary, HARDWARE, SUMMARY_SCOPE, account.id(), 200);
        assertThat(valid.get("sessionCount").asLong()).isZero();
    }

    @Test
    void contractRoutesAcceptOnlyGetOnTheDedicatedPaths() throws Exception {
        UUID account = UUID.randomUUID();
        mvcStatus(post(INTERNAL + account + "/performance-summary?hardwareProfileId=" + UUID.randomUUID())
                .header("Authorization", "Bearer " + serviceToken(HARDWARE, SUMMARY_SCOPE, account, "performance-service")),
                403);
        mvcStatus(get(INTERNAL + account + "/performance-secrets").header("Authorization", "Bearer "
                + serviceToken(HARDWARE, SUMMARY_SCOPE, account, "performance-service")), 403);
    }
}

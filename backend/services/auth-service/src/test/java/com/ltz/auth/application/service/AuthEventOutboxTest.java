package com.ltz.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.jayway.jsonpath.JsonPath;
import com.ltz.auth.IntegrationTestSupport;
import com.ltz.auth.RecordingMailPortConfiguration;
import com.ltz.auth.RecordingMailPortConfiguration.RecordingMailPort;
import com.ltz.auth.api.controller.AuthApiPaths;
import com.ltz.auth.domain.enums.AccountStatus;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * AUTH-19 olay çıkışı (transactional outbox): olay iş değişikliğiyle aynı işlemde hedef başına bir satır olarak yazılır,
 * aktarıcı servis token'ıyla teslim eder, başarısız hedef geri çekilmeyle beklemede kalır, teslim edilenler saklama
 * süresi sonunda silinir. Hedefler: {@code community} (gövdeyi yakalayan yerel sunucu) ve {@code lfp} (erişilemez adres).
 */
@Import(RecordingMailPortConfiguration.class)
class AuthEventOutboxTest extends IntegrationTestSupport {

    private static final CapturingServer COMMUNITY = CapturingServer.start();

    @Autowired
    private AuthEventRelay relay;

    @Autowired
    private AccountAdministrationService administration;

    @Autowired
    private RecordingMailPort mailPort;

    @DynamicPropertySource
    static void eventTargets(DynamicPropertyRegistry registry) {
        registry.add("ltz.auth.events.targets", () -> "community,lfp");
        registry.add("ltz.auth.deletion.service-urls.community", COMMUNITY::baseUrl);
        registry.add("ltz.auth.deletion.service-urls.lfp", () -> "http://127.0.0.1:1");
        // Başarısız hedef testte yeniden denenmesin: geri çekilme bir saat.
        registry.add("ltz.auth.events.initial-backoff", () -> "PT1H");
        registry.add("ltz.auth.events.max-backoff", () -> "PT1H");
    }

    @AfterAll
    static void stop() {
        COMMUNITY.close();
    }

    // ---- Yazım: iş değişikliğiyle birlikte, hedef başına ----

    @Test
    void durumDegisikligiHerHedefIcinTekEventIdliSatirYazar() throws Exception {
        UUID account = newActive();

        administration.changeStatus(newActive(), account, AccountStatus.SUSPENDED, "test");

        List<Map<String, Object>> rows = outbox(account);
        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(row -> row.get("target")).containsExactly("community", "lfp");
        assertThat(rows).extracting(row -> row.get("event_type")).containsOnly("ACCOUNT_STATUS_CHANGED");
        assertThat(rows).extracting(row -> row.get("payload_value")).containsOnly("SUSPENDED");
        assertThat(rows).extracting(row -> row.get("status")).containsOnly("PENDING");
        assertThat(rows.get(0)).as("aynı olay, ortak eventId").containsEntry("event_id", rows.get(1).get("event_id"));
    }

    @Test
    void emailDogrulaninceUserRegisteredKullaniciAdiylaYazilir() throws Exception {
        String email = uniqueEmail();
        String username = uniqueUsername();
        register(email, username, VALID_PASSWORD).andExpect(status().isAccepted());
        UUID account = accountId(email);
        assertThat(outbox(account)).as("doğrulanmamış hesap için olay yok").isEmpty();

        confirm(email, mailPort.awaitLatestCode(email, 1)).andExpect(status().isNoContent());

        List<Map<String, Object>> rows = outbox(account);
        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(row -> row.get("event_type")).containsOnly("USER_REGISTERED");
        assertThat(rows).extracting(row -> row.get("payload_value")).containsOnly(username);
    }

    @Test
    void emailDegisincedeAccountEmailChangedDegersizYazilirAdresTasinmaz() throws Exception {
        UUID account = newActive();
        String bearer = bearerFor(account);
        String newEmail = uniqueEmail();
        mockMvc.perform(post(AuthApiPaths.EMAIL_CHANGE).header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newEmail\":\"%s\",\"password\":\"%s\"}".formatted(newEmail, VALID_PASSWORD)))
                .andExpect(status().isAccepted());

        mockMvc.perform(post(AuthApiPaths.EMAIL_CHANGE_CONFIRM).header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"%s\"}".formatted(mailPort.awaitLatestCode(newEmail, 1))))
                .andExpect(status().isNoContent());

        List<Map<String, Object>> rows = outbox(account);
        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(row -> row.get("event_type")).containsOnly("ACCOUNT_EMAIL_CHANGED");
        assertThat(rows).extracting(row -> row.get("payload_value")).containsOnlyNulls();
    }

    // ---- Teslim ----

    @Test
    void aktarimZarfiServisTokeniylaTeslimEderHataliHedefGeriCekilmeylePendingKalir() throws Exception {
        UUID account = newActive();
        administration.changeStatus(newActive(), account, AccountStatus.BANNED, "test");

        relay.processDue();

        Map<String, Object> community = row(account, "community");
        Map<String, Object> lfp = row(account, "lfp");
        assertThat(community).containsEntry("status", "DELIVERED");
        assertThat(lfp).containsEntry("status", "PENDING");
        assertThat(lfp).containsEntry("attempts", 1);
        assertThat(lfp).containsEntry("last_error", "EVENT_UNREACHABLE");
        assertThat(((Timestamp) lfp.get("next_attempt_at")).toInstant()).isAfter(Instant.now().plusSeconds(1800));

        CapturingServer.Request request = COMMUNITY.requests().stream()
                .filter(captured -> captured.body().contains(account.toString())).findFirst().orElseThrow();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/internal/events");
        SignedJWT jwt = SignedJWT.parse(request.authorization().substring("Bearer ".length()));
        JWTClaimsSet claims = jwt.getJWTClaimsSet();
        assertThat(jwt.getHeader().getType().getType()).isEqualTo("svc+jwt");
        assertThat(claims.getSubject()).isEqualTo("svc:auth-service");
        assertThat(claims.getAudience()).containsExactly("community-service");
        assertThat(claims.getStringClaim("scope")).isEqualTo("events:ingest");
        assertThat(claims.getStringClaim("account_id")).isEqualTo(account.toString());
        assertThat((String) JsonPath.read(request.body(), "$.eventType")).isEqualTo("AccountStatusChanged");
        assertThat((Integer) JsonPath.read(request.body(), "$.eventVersion")).isEqualTo(1);
        assertThat((String) JsonPath.read(request.body(), "$.producer")).isEqualTo("auth-service");
        assertThat((String) JsonPath.read(request.body(), "$.aggregateId")).isEqualTo(account.toString());
        assertThat((String) JsonPath.read(request.body(), "$.payload.accountId")).isEqualTo(account.toString());
        assertThat((String) JsonPath.read(request.body(), "$.payload.status")).isEqualTo("BANNED");
        assertThat((String) JsonPath.read(request.body(), "$.eventId")).isEqualTo(community.get("event_id").toString());
        assertThat(request.body()).as("kişisel veri taşınmaz").doesNotContain("@").doesNotContain("email");
    }

    /** Community alıcısının uyguladığı kurallar: ömür, zaman alanları, jti ve zarf üreticisi ile token öznesinin eşleşmesi. */
    @Test
    void zarfVeTokenCommunityAliciKurallarinaUyar() throws Exception {
        UUID account = newActive();
        administration.changeStatus(newActive(), account, AccountStatus.SUSPENDED, "test");

        relay.processDue();

        CapturingServer.Request request = COMMUNITY.requests().stream()
                .filter(captured -> captured.body().contains(account.toString())).findFirst().orElseThrow();
        JWTClaimsSet claims = SignedJWT.parse(request.authorization().substring("Bearer ".length())).getJWTClaimsSet();
        assertThat(claims.getIssueTime()).isNotNull();
        assertThat(claims.getNotBeforeTime()).isNotNull();
        assertThat(claims.getJWTID()).isNotBlank();
        assertThat(claims.getExpirationTime().getTime() - claims.getIssueTime().getTime())
                .as("ömür en çok 60 sn").isPositive().isLessThanOrEqualTo(60_000L);
        assertThat(claims.getSubject()).as("token öznesi svc:<zarf üreticisi>")
                .isEqualTo("svc:" + JsonPath.read(request.body(), "$.producer"));
        Map<String, Object> payload = JsonPath.read(request.body(), "$.payload");
        assertThat(payload).containsOnlyKeys("accountId", "status");
        assertThat(payload).containsEntry("status", "SUSPENDED");
        assertThat((String) JsonPath.read(request.body(), "$.aggregateId")).isEqualTo(payload.get("accountId"));
    }

    @Test
    void teslimEdilenSatirlarSaklamaSuresiSonundaSilinirBekleyenKalir() throws Exception {
        UUID account = newActive();
        administration.changeStatus(newActive(), account, AccountStatus.SUSPENDED, "test");
        relay.processDue();
        jdbc.update("update auth_event_outbox set delivered_at = now() - interval '10 days' "
                + "where account_id = ? and status = 'DELIVERED'", account);

        relay.processDue();

        assertThat(outbox(account)).extracting(row -> row.get("target")).containsExactly("lfp");
    }

    @Test
    void hesapSatiriSilinirkenOutboxSatirlariCascadeIleGider() throws Exception {
        UUID account = newActive();
        administration.changeStatus(newActive(), account, AccountStatus.SUSPENDED, "test");
        assertThat(outbox(account)).hasSize(2);

        jdbc.update("delete from security_events where account_id = ?", account);
        jdbc.update("delete from account_status_history where account_id = ?", account);
        jdbc.update("delete from accounts where id = ?", account);

        assertThat(outbox(account)).isEmpty();
    }

    // ---- Yardımcılar ----

    private UUID newActive() throws Exception {
        return accountId(registerActiveAccount(uniqueEmail(), uniqueUsername()));
    }

    private List<Map<String, Object>> outbox(UUID account) {
        return jdbc.queryForList("select event_id, target, event_type, payload_value, status, attempts, last_error, "
                + "next_attempt_at from auth_event_outbox where account_id = ? order by target", account);
    }

    private Map<String, Object> row(UUID account, String target) {
        return outbox(account).stream().filter(row -> target.equals(row.get("target"))).findFirst().orElseThrow();
    }

    /** Yalnız test: gelen istekleri (yöntem, yol, Authorization, gövde) kaydeden ve 200 dönen yerel HTTP sunucusu. */
    private static final class CapturingServer implements AutoCloseable {

        record Request(String method, String path, String authorization, String body) {

            @Override
            public String toString() {
                return "Request[method=" + method + ", path=" + path + ", authorization=***]";
            }
        }

        private final HttpServer server;
        private final List<Request> requests = new CopyOnWriteArrayList<>();

        private CapturingServer(HttpServer server) {
            this.server = server;
            server.createContext("/", this::handle);
        }

        static CapturingServer start() {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
                CapturingServer capturing = new CapturingServer(server);
                server.start();
                return capturing;
            } catch (IOException exception) {
                throw new IllegalStateException("Capturing server could not start", exception);
            }
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        List<Request> requests() {
            return List.copyOf(requests);
        }

        private void handle(HttpExchange exchange) throws IOException {
            try (exchange) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                requests.add(new Request(exchange.getRequestMethod(), exchange.getRequestURI().getRawPath(),
                        exchange.getRequestHeaders().getFirst("Authorization"), body));
                exchange.sendResponseHeaders(200, -1);
            }
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}

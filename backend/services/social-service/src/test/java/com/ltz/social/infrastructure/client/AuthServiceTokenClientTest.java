package com.ltz.social.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.ltz.social.domain.exception.ServiceTokenUnavailableException;
import com.ltz.social.infrastructure.config.ServiceClientProperties;

/** Auth'tan hedefe bağlı servis token'ı alma: istek biçimi, kısa önbellek, fail-closed ve sızıntısız hatalar. */
class AuthServiceTokenClientTest {

    private static final String SECRET = "test-only-client-secret";
    private static final String TOKEN_URL = "http://auth-service:8080/internal/v1/service-tokens";

    private MutableClock clock;
    private RestClient.Builder builder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-29T10:00:00Z"));
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
    }

    private AuthServiceTokenClient client(String secret) {
        return new AuthServiceTokenClient(builder, clock, new ServiceClientProperties(
                "http://auth-service:8080", "/internal/v1/service-tokens", "social-service", secret,
                "http://community-service:8080"));
    }

    private static String tokenResponse(String token, int expiresIn) {
        return "{\"accessToken\":\"%s\",\"tokenType\":\"Bearer\",\"expiresIn\":%d,\"scope\":\"events:ingest\",\"extra\":1}"
                .formatted(token, expiresIn);
    }

    @Test
    void istekClientIdSecretVeHedefiTasirTokenDoner() {
        server.expect(once(), requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.clientId").value("social-service"))
                .andExpect(jsonPath("$.clientSecret").value(SECRET))
                .andExpect(jsonPath("$.audience").value("community-service"))
                .andRespond(withSuccess(tokenResponse("tok-1", 60), MediaType.APPLICATION_JSON));

        assertThat(client(SECRET).tokenFor("community-service")).isEqualTo("tok-1");

        server.verify();
    }

    @Test
    void ayniHedefIcinGecerlilikSuresiIcindeYenidenKullanilirSureDolunca1yenilenir() {
        AuthServiceTokenClient client = client(SECRET);
        server.expect(once(), requestTo(TOKEN_URL))
                .andRespond(withSuccess(tokenResponse("tok-1", 60), MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(TOKEN_URL))
                .andRespond(withSuccess(tokenResponse("tok-2", 60), MediaType.APPLICATION_JSON));

        assertThat(client.tokenFor("community-service")).isEqualTo("tok-1");
        clock.advance(Duration.ofSeconds(30));
        assertThat(client.tokenFor("community-service")).isEqualTo("tok-1");
        // 60 sn - 15 sn güvenlik payı dolunca yenilenir.
        clock.advance(Duration.ofSeconds(20));
        assertThat(client.tokenFor("community-service")).isEqualTo("tok-2");

        server.verify();
    }

    @Test
    void farkliHedefIcinAyriTokenAlinir() {
        AuthServiceTokenClient client = client(SECRET);
        server.expect(once(), requestTo(TOKEN_URL)).andExpect(jsonPath("$.audience").value("community-service"))
                .andRespond(withSuccess(tokenResponse("tok-c", 60), MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(TOKEN_URL)).andExpect(jsonPath("$.audience").value("notification-service"))
                .andRespond(withSuccess(tokenResponse("tok-n", 60), MediaType.APPLICATION_JSON));

        assertThat(client.tokenFor("community-service")).isEqualTo("tok-c");
        assertThat(client.tokenFor("notification-service")).isEqualTo("tok-n");

        server.verify();
    }

    @Test
    void kisaOmurluTokenOnbellegeAlinmaz() {
        AuthServiceTokenClient client = client(SECRET);
        server.expect(once(), requestTo(TOKEN_URL))
                .andRespond(withSuccess(tokenResponse("tok-1", 10), MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(TOKEN_URL))
                .andRespond(withSuccess(tokenResponse("tok-2", 10), MediaType.APPLICATION_JSON));

        assertThat(client.tokenFor("community-service")).isEqualTo("tok-1");
        assertThat(client.tokenFor("community-service")).isEqualTo("tok-2");

        server.verify();
    }

    @Test
    void gizliAnahtarYoksaKapaliVeAuthCagrilmaz() {
        assertThat(client("").isConfigured()).isFalse();
        assertThat(client(null).isConfigured()).isFalse();
        assertThat(client("  ").isConfigured()).isFalse();
        assertThat(client(SECRET).isConfigured()).isTrue();

        assertThatThrownBy(() -> client("").tokenFor("community-service"))
                .isInstanceOf(ServiceTokenUnavailableException.class);

        server.verify();
    }

    @Test
    void authReddederseHataGizliAnahtarVeGovdeIcermez() {
        server.expect(once(), requestTo(TOKEN_URL))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"INVALID_CLIENT\",\"echo\":\"" + SECRET + "\"}"));

        assertThatThrownBy(() -> client(SECRET).tokenFor("community-service"))
                .isInstanceOf(ServiceTokenUnavailableException.class)
                .hasMessageNotContaining(SECRET)
                .hasMessageNotContaining("INVALID_CLIENT");
    }

    @Test
    void bosTokenDonerseHata() {
        server.expect(once(), requestTo(TOKEN_URL))
                .andRespond(withSuccess(tokenResponse("", 60), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client(SECRET).tokenFor("community-service"))
                .isInstanceOf(ServiceTokenUnavailableException.class);
    }

    @Test
    void ayarlarinToStringiGizliAnahtariYazmaz() {
        ServiceClientProperties properties = new ServiceClientProperties("http://auth-service:8080",
                "/internal/v1/service-tokens", "social-service", SECRET, "http://community-service:8080");

        assertThat(properties.toString()).doesNotContain(SECRET);
    }

    /** Testte ilerletilebilen saat. */
    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}

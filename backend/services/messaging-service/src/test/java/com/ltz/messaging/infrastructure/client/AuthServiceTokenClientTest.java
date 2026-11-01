package com.ltz.messaging.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.never;
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
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.ltz.messaging.application.port.ServiceTokenUnavailableException;
import com.ltz.messaging.infrastructure.config.ServiceClientProperties;

/** Auth'tan hedefe bağlı servis token'ı: istek biçimi, kısa yeniden kullanım, fail-closed ve sızıntısız hatalar. */
class AuthServiceTokenClientTest {

    private static final String SECRET = "test-only-client-secret";
    private static final String TOKEN_URL = "http://auth-service:8080/internal/v1/service-tokens";

    private MutableClock clock;
    private RestClient.Builder builder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-11-01T10:00:00Z"));
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
    }

    private AuthServiceTokenClient client(String secret) {
        return new AuthServiceTokenClient(builder, clock, new ServiceClientProperties("http://auth-service:8080",
                "/internal/v1/service-tokens", "messaging-service", secret, "http://social-service:8080",
                "/internal/v1/blocks/check"));
    }

    private static String token(String value, long expiresIn) {
        return "{\"accessToken\":\"%s\",\"tokenType\":\"Bearer\",\"expiresIn\":%d,\"scope\":\"blocks:check\"}"
                .formatted(value, expiresIn);
    }

    @Test
    void istekIstemciKimligiGizliAnahtarVeHedefiTasir() {
        server.expect(once(), requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.clientId").value("messaging-service"))
                .andExpect(jsonPath("$.clientSecret").value(SECRET))
                .andExpect(jsonPath("$.audience").value("social-service"))
                .andRespond(withSuccess(token("tok-1", 60), MediaType.APPLICATION_JSON));

        assertThat(client(SECRET).tokenFor("social-service")).isEqualTo("tok-1");
        server.verify();
    }

    @Test
    void gecerlilikSuresiIcindeYenidenKullanilirPayKalincaYenilenir() {
        AuthServiceTokenClient client = client(SECRET);
        server.expect(once(), requestTo(TOKEN_URL)).andRespond(withSuccess(token("tok-1", 60), MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(TOKEN_URL)).andRespond(withSuccess(token("tok-2", 60), MediaType.APPLICATION_JSON));

        assertThat(client.tokenFor("social-service")).isEqualTo("tok-1");
        clock.advance(Duration.ofSeconds(44));
        assertThat(client.tokenFor("social-service")).isEqualTo("tok-1");
        clock.advance(Duration.ofSeconds(1));
        assertThat(client.tokenFor("social-service")).isEqualTo("tok-2");
        server.verify();
    }

    @Test
    void uzunOmurBildirilseDeAltmisSaniyedenFazlaTutulmazKisaOmurTutulmaz() {
        AuthServiceTokenClient client = client(SECRET);
        server.expect(once(), requestTo(TOKEN_URL))
                .andRespond(withSuccess(token("long", 3600), MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(TOKEN_URL)).andRespond(withSuccess(token("short", 10), MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(TOKEN_URL)).andRespond(withSuccess(token("next", 60), MediaType.APPLICATION_JSON));

        assertThat(client.tokenFor("social-service")).isEqualTo("long");
        clock.advance(Duration.ofSeconds(45));
        assertThat(client.tokenFor("social-service")).isEqualTo("short");
        assertThat(client.tokenFor("social-service")).isEqualTo("next");
        server.verify();
    }

    @Test
    void gizliAnahtarYoksaAuthCagrilmaz() {
        server.expect(never(), requestTo(TOKEN_URL));

        AuthServiceTokenClient emptySecret = client("");
        AuthServiceTokenClient noSecret = client(null);
        assertThatThrownBy(() -> emptySecret.tokenFor("social-service"))
                .isInstanceOf(ServiceTokenUnavailableException.class);
        assertThatThrownBy(() -> noSecret.tokenFor("social-service"))
                .isInstanceOf(ServiceTokenUnavailableException.class);
        server.verify();
    }

    @Test
    void authReddederseYaDaTokenVermezseHataGizliBilgiIcermez() {
        server.expect(once(), requestTo(TOKEN_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .body("{\"code\":\"INVALID_CLIENT\",\"echo\":\"" + SECRET + "\"}"));
        server.expect(once(), requestTo(TOKEN_URL)).andRespond(withSuccess("{\"expiresIn\":60}",
                MediaType.APPLICATION_JSON));
        AuthServiceTokenClient client = client(SECRET);

        assertThatThrownBy(() -> client.tokenFor("social-service"))
                .isInstanceOf(ServiceTokenUnavailableException.class)
                .hasMessageNotContaining(SECRET);
        assertThatThrownBy(() -> client.tokenFor("social-service"))
                .isInstanceOf(ServiceTokenUnavailableException.class);
    }

    @Test
    void ozellikMetniGizliAnahtariGostermez() {
        assertThat(new ServiceClientProperties("http://auth-service:8080", "/t", "messaging-service", SECRET,
                "http://social-service:8080", "/internal/v1/blocks/check").toString()).doesNotContain(SECRET);
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}

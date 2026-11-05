package com.ltz.game.infrastructure.client;

import com.ltz.game.domain.exception.ServiceTokenUnavailableException;
import com.ltz.game.infrastructure.client.FakeServer.Reply;
import com.ltz.game.infrastructure.config.GameRatingConfiguration;
import com.ltz.game.infrastructure.config.GameRatingProperties;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthServiceTokenClientTest {
    private static final String SECRET = "test-only-secret-value";
    private static final String OK = "{\"accessToken\":\"token-%d\",\"tokenType\":\"Bearer\",\"expiresIn\":60,\"scope\":\"rating-summaries:read\",\"extra\":1}";

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-05T10:00:00Z");

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void advance(Duration duration) { now = now.plus(duration); }
    }

    private static GameRatingProperties properties(String baseUrl, String secret) {
        return new GameRatingProperties("http://unused", baseUrl, "/internal/v1/service-tokens", "game-service", secret,
                "review-service", Duration.ofSeconds(1), Duration.ofMillis(500), 500, new GameRatingProperties.Sync(true,
                        Duration.ofMinutes(5), Duration.ofSeconds(30), Duration.ofMinutes(2)));
    }

    private static AuthServiceTokenClient client(FakeServer auth, MutableClock clock, String secret) {
        GameRatingProperties properties = properties(auth.baseUrl(), secret);
        RestClient http = GameRatingConfiguration.restClient(RestClient.builder(), auth.baseUrl(),
                properties.connectTimeout(), properties.readTimeout());
        return new AuthServiceTokenClient(http, clock, properties);
    }

    @Test void requestsAudienceBoundTokenAndSendsOnlyDocumentedFields() throws IOException {
        try (FakeServer auth = new FakeServer()) {
            auth.enqueue(Reply.json(200, OK.formatted(1)));
            assertEquals("token-1", client(auth, new MutableClock(), SECRET).tokenFor("review-service"));
            var request = auth.received().getFirst();
            assertEquals("POST", request.method());
            assertEquals("/internal/v1/service-tokens", request.pathAndQuery());
            assertEquals("{\"clientId\":\"game-service\",\"clientSecret\":\"" + SECRET + "\",\"audience\":\"review-service\"}", request.body());
        }
    }

    @Test void reusesTokenUntilRefreshMarginThenFetchesANewOne() throws IOException {
        try (FakeServer auth = new FakeServer()) {
            auth.enqueue(Reply.json(200, OK.formatted(1)), Reply.json(200, OK.formatted(2)));
            var clock = new MutableClock();
            var tokens = client(auth, clock, SECRET);
            assertEquals("token-1", tokens.tokenFor("review-service"));
            clock.advance(Duration.ofSeconds(44));
            assertEquals("token-1", tokens.tokenFor("review-service"));
            assertEquals(1, auth.received().size());
            clock.advance(Duration.ofSeconds(2)); // 46 sn: bitime 15 sn payı kalmadı
            assertEquals("token-2", tokens.tokenFor("review-service"));
            assertEquals(2, auth.received().size());
        }
    }

    @Test void invalidateDiscardsTheCachedTokenForThatAudienceOnly() throws IOException {
        try (FakeServer auth = new FakeServer()) {
            auth.enqueue(Reply.json(200, OK.formatted(1)), Reply.json(200, OK.formatted(2)), Reply.json(200, OK.formatted(3)));
            var tokens = client(auth, new MutableClock(), SECRET);
            assertEquals("token-1", tokens.tokenFor("review-service"));
            assertEquals("token-2", tokens.tokenFor("other-service"));
            tokens.invalidate("review-service");
            assertEquals("token-3", tokens.tokenFor("review-service"));
            assertEquals("token-2", tokens.tokenFor("other-service"));
        }
    }

    @Test void unconfiguredClientNeverCallsAuth() throws IOException {
        try (FakeServer auth = new FakeServer()) {
            for (String secret : new String[]{null, "", "  "}) {
                var tokens = client(auth, new MutableClock(), secret);
                assertFalse(tokens.isConfigured());
                assertThrows(ServiceTokenUnavailableException.class, () -> tokens.tokenFor("review-service"));
            }
            assertTrue(auth.received().isEmpty());
        }
    }

    @Test void authRefusalIsNotRetriedAndLeaksNeitherSecretNorBody() throws IOException {
        try (FakeServer auth = new FakeServer()) {
            auth.enqueue(Reply.json(401, "{\"detail\":\"secret echo " + SECRET + "\"}"));
            var tokens = client(auth, new MutableClock(), SECRET);
            var error = assertThrows(ServiceTokenUnavailableException.class, () -> tokens.tokenFor("review-service"));
            assertEquals(1, auth.received().size());
            assertFalse(error.getMessage().contains(SECRET));
            assertFalse(String.valueOf(error).contains("echo"));
            assertFalse(tokens.toString().contains(SECRET));
        }
    }

    @Test void emptyTokenIsRejectedAndNeverCached() throws IOException {
        try (FakeServer auth = new FakeServer()) {
            auth.enqueue(Reply.json(200, "{\"accessToken\":\"\",\"expiresIn\":60}"), Reply.json(200, OK.formatted(7)));
            var tokens = client(auth, new MutableClock(), SECRET);
            assertThrows(ServiceTokenUnavailableException.class, () -> tokens.tokenFor("review-service"));
            assertEquals("token-7", tokens.tokenFor("review-service"));
        }
    }

    @Test void shortLivedTokenIsNotCached() throws IOException {
        try (FakeServer auth = new FakeServer()) {
            auth.enqueue(Reply.json(200, "{\"accessToken\":\"short\",\"expiresIn\":10}"), Reply.json(200, OK.formatted(8)));
            var tokens = client(auth, new MutableClock(), SECRET);
            assertEquals("short", tokens.tokenFor("review-service"));
            assertEquals("token-8", tokens.tokenFor("review-service"));
        }
    }

    @Test void propertiesToStringHidesTheSecret() {
        assertFalse(properties("http://auth", SECRET).toString().contains(SECRET));
    }
}

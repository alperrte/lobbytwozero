package com.ltz.review.api.controller;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Gerçek TCP + imza/JWKS + RestClient + PostgreSQL; Auth/Game uçları yerel sözleşme sunucusudur. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReviewHttpIntegrationTest {
    static final UUID GAME = UUID.randomUUID();
    static final UUID USER = UUID.randomUUID();
    static final KeyPair KEYS = keys();
    static final AtomicBoolean GAME_AVAILABLE = new AtomicBoolean(true);
    static final AtomicBoolean GAME_DELAY = new AtomicBoolean(false);
    static final HttpServer DEPENDENCIES = dependencies();
    @LocalServerPort int port;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        String base = "http://127.0.0.1:" + DEPENDENCIES.getAddress().getPort();
        registry.add("ltz.security.jwt.jwk-set-uri", () -> base + "/jwks");
        registry.add("GAME_SERVICE_URL", () -> base);
        registry.add("spring.http.clients.read-timeout", () -> "200ms");
    }

    @AfterAll static void stop() { DEPENDENCIES.stop(0); }

    @Test void signedHttpFlowPreservesIndependentFieldsAndBlocksOldTokenAfterErasure() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            String user = token(false, USER);
            String base = "/api/v1/review/games/" + GAME;
            var rating = send(client, "PUT", base + "/rating", "{\"rating\":7}", user);
            assertEquals(200, rating.statusCode());
            assertEquals("private, no-store", rating.headers().firstValue("Cache-Control").orElseThrow());
            assertEquals(404, send(client, "GET", base + "/recommendation", null, user).statusCode());
            assertEquals(200, send(client, "PUT", base + "/recommendation", "{\"recommended\":false}", user).statusCode());
            var created = send(client, "POST", base + "/reviews", "{\"body\":\"HTTP deneyimi\"}", user);
            assertEquals(201, created.statusCode());
            String review = created.headers().firstValue("Location").orElseThrow();
            assertEquals(403, send(client, "PATCH", review, "{\"version\":0,\"body\":\"Başka yazar\"}", token(false, UUID.randomUUID())).statusCode());
            assertEquals(200, send(client, "PATCH", review, "{\"version\":0,\"body\":\"Yeni deneyim\"}", user).statusCode());
            assertEquals(204, send(client, "DELETE", review + "?version=1", null, user).statusCode());
            assertEquals(404, send(client, "GET", review, null, user).statusCode());
            assertTrue(send(client, "GET", base + "/rating", null, user).body().contains("\"rating\":7"));
            assertTrue(send(client, "GET", base + "/recommendation", null, user).body().contains("\"recommended\":false"));
            assertEquals(204, send(client, "DELETE", "/internal/v1/accounts/" + USER, null, token(true, USER)).statusCode());
            assertEquals(204, send(client, "DELETE", "/internal/v1/accounts/" + USER, null, token(true, USER)).statusCode());
            assertEquals(403, send(client, "PUT", base + "/rating", "{\"rating\":7}", user).statusCode());
            assertEquals(404, send(client, "GET", base + "/rating", null, user).statusCode());
        }
    }

    @Test void signedHttpFeaturedListRequiresUserTokenAndValidatesPagination() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            String path = "/api/v1/review/games/" + UUID.randomUUID() + "/reviews/featured";
            var response = send(client, "GET", path, null, token(false, UUID.randomUUID()));
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"items\":[]"));
            assertEquals("private, no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            assertEquals(401, send(client, "GET", path, null, token(true, USER)).statusCode());
            assertEquals(400, send(client, "GET", path + "?size=51", null, token(false, UUID.randomUUID())).statusCode());
        }
    }

    @Test void signedHttpRatingSummaryKeepsUserAndServiceIdentitiesSeparate() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            String path = "/api/v1/review/games/" + UUID.randomUUID() + "/rating-summary";
            var result = send(client, "GET", path, null, token(false, UUID.randomUUID()));
            assertEquals(200, result.statusCode());
            assertTrue(result.body().contains("\"averageRating\":null"));
            assertTrue(result.body().contains("\"ratingCount\":0"));
            assertEquals(401, send(client, "GET", path, null, token(true, USER)).statusCode());
        }
    }

    @Test void signedHttpBatchSummaryRejectsServiceTokenAndInvalidPayload() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            String path = "/api/v1/review/games/rating-summaries";
            String user = token(false, UUID.randomUUID());
            var result = send(client, "POST", path, "{\"gameIds\":[\"" + UUID.randomUUID() + "\"]}", user);
            assertEquals(200, result.statusCode());
            assertTrue(result.body().contains("\"averageRating\":null"));
            assertEquals(401, send(client, "POST", path, "{\"gameIds\":[]}", token(true, USER)).statusCode());
            assertEquals(400, send(client, "POST", path, "{\"gameIds\":[null]}", user).statusCode());
        }
    }

    @Test void actualGameHttpFailureIsControlledAndDoesNotLeakAddressOrAcceptGame() throws Exception {
        GAME_AVAILABLE.set(false);
        try (var client = HttpClient.newHttpClient()) {
            var response = send(client, "PUT", "/api/v1/review/games/" + GAME + "/rating", "{\"rating\":7}", token(false, UUID.randomUUID()));
            assertEquals(503, response.statusCode());
            assertFalse(response.body().contains("127.0.0.1"));
            assertFalse(response.body().contains("Exception"));
        } finally {
            GAME_AVAILABLE.set(true);
        }
    }

    @Test void chunkedBodyCannotBypassRequestSizeLimit() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            byte[] oversized = ("{\"body\":\"" + "a".repeat(65536) + "\"}").getBytes(StandardCharsets.UTF_8);
            var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/review/games/" + GAME + "/reviews"))
                    .timeout(Duration.ofSeconds(10)).header("Authorization", "Bearer " + token(false, UUID.randomUUID()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new java.io.ByteArrayInputStream(oversized))).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(413, response.statusCode());
            assertTrue(response.body().contains("\"status\":413"));
        }
    }

    @Test void actualGameReadTimeoutReturnsControlled503() throws Exception {
        GAME_DELAY.set(true);
        try (var client = HttpClient.newHttpClient()) {
            var response = send(client, "PUT", "/api/v1/review/games/" + GAME + "/rating", "{\"rating\":7}", token(false, UUID.randomUUID()));
            assertEquals(503, response.statusCode());
        } finally {
            GAME_DELAY.set(false);
        }
    }

    @Test void signedHttpLikesAreIdempotentAndRejectSelfAndServiceIdentity() throws Exception {
        UUID owner = UUID.randomUUID();
        String ownerToken = token(false, owner);
        String voterToken = token(false, UUID.randomUUID());
        try (var client = HttpClient.newHttpClient()) {
            var created = send(client, "POST", "/api/v1/review/games/" + GAME + "/reviews", "{\"body\":\"Beğeni deneyimi\"}", ownerToken);
            assertEquals(201, created.statusCode());
            String reviewPath = created.headers().firstValue("Location").orElseThrow();
            String likePath = reviewPath + "/like";
            assertEquals(403, send(client, "PUT", likePath, null, ownerToken).statusCode());
            assertEquals(401, send(client, "PUT", likePath, null, token(true, owner)).statusCode());
            for (int i = 0; i < 2; i++) {
                var liked = send(client, "PUT", likePath, null, voterToken);
                assertEquals(200, liked.statusCode());
                assertTrue(liked.body().contains("\"likeCount\":1"));
                assertTrue(liked.body().contains("\"likedByActor\":true"));
            }
            var authorView = send(client, "GET", likePath, null, ownerToken);
            assertEquals(200, authorView.statusCode());
            assertTrue(authorView.body().contains("\"likedByActor\":false"));
            for (int i = 0; i < 2; i++) {
                var removed = send(client, "DELETE", likePath, null, voterToken);
                assertEquals(200, removed.statusCode());
                assertTrue(removed.body().contains("\"likeCount\":0"));
            }
            assertEquals(204, send(client, "DELETE", reviewPath + "?version=0", null, ownerToken).statusCode());
            assertEquals(404, send(client, "PUT", likePath, null, voterToken).statusCode());
        }
    }

    HttpResponse<String> send(HttpClient client, String method, String path, String body, String token) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    static String token(boolean service, UUID account) throws Exception {
        Instant now = Instant.now();
        var claims = new JWTClaimsSet.Builder().issuer("ltz-auth").audience(service ? "review-service" : "ltz-api")
                .subject(service ? "svc:auth-service" : account.toString()).jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now)).notBeforeTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(service ? 30 : 900)))
                .claim("roles", List.of("PLAYER")).claim("scope", "account:erase").claim("account_id", account.toString()).build();
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).type(new JOSEObjectType(service ? "svc+jwt" : "at+jwt"))
                .keyID("http-test").build(), claims);
        jwt.sign(new RSASSASigner(KEYS.getPrivate()));
        return jwt.serialize();
    }

    static HttpServer dependencies() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(command -> Thread.startVirtualThread(command));
            byte[] jwks = new JWKSet(new RSAKey.Builder((java.security.interfaces.RSAPublicKey) KEYS.getPublic())
                    .keyID("http-test").build()).toString().getBytes(StandardCharsets.UTF_8);
            server.createContext("/jwks", exchange -> {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, jwks.length);
                try (var output = exchange.getResponseBody()) { output.write(jwks); }
                exchange.close();
            });
            server.createContext("/api/v1/game/games/batch", exchange -> {
                exchange.getRequestBody().readAllBytes();
                if (GAME_DELAY.get()) {
                    try { Thread.sleep(750); }
                    catch (InterruptedException error) { Thread.currentThread().interrupt(); }
                }
                byte[] result = JsonMapper.builder().build().writeValueAsBytes(java.util.Map.of(
                        "items", List.of(java.util.Map.of("gameId", GAME.toString(), "title", "Oyun")), "unavailable", List.of()));
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(GAME_AVAILABLE.get() ? 200 : 503, result.length);
                try (var output = exchange.getResponseBody()) { output.write(result); }
                exchange.close();
            });
            server.start();
            return server;
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    static KeyPair keys() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(3072);
            return generator.generateKeyPair();
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }
}

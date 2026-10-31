package com.ltz.review.infrastructure.security;

import com.ltz.review.application.port.GameCatalogPort;
import com.ltz.review.infrastructure.persistence.repository.AccountWriteLock;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Review'ın GERÇEK HTTP sunucusu (RANDOM_PORT, gerçek güvenlik zinciri, PostgreSQL), Auth'un GERÇEK üretim kaynaklarından
 * (AccessTokenIssuer, ServiceTokenIssuer, JwksController, HttpAccountDataEraser) türeyen token ve silme çağrılarıyla sınanır.
 * Auth kaynakları değiştirilmez; yalnız geçici dizine derlenir. Canlı Auth servisi ve gerçek gizli anahtar kullanılmaz,
 * bu nedenle canlı Auth↔Review uçtan uca kanıtı değildir.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthIssuedTokenIntegrationTest {
    static final KeyPair KEYS = keys();
    static final ProductionAuthSupport AUTH = auth();
    static final HttpServer JWKS = jwks();
    @LocalServerPort int port;
    @Autowired JdbcClient jdbc;
    @MockitoBean GameCatalogPort games;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("ltz.security.jwt.jwk-set-uri", () -> "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/jwks");
    }

    @AfterAll static void stop() throws Exception {
        JWKS.stop(0);
        AUTH.close();
    }

    @Test void userTokensIssuedByAuthAreAcceptedForEveryCanonicalRoleAndAMultiRoleAccount() throws Exception {
        String path = "/api/v1/review/games/" + UUID.randomUUID() + "/statistics";
        try (var client = HttpClient.newHttpClient()) {
            for (String role : List.of("PLAYER", "CONTENT_CREATOR", "GAME_DEVELOPER", "MODERATOR", "ADMIN"))
                assertEquals(200, send(client, "GET", path, null, AUTH.userToken(UUID.randomUUID(), role)).statusCode(), role);
            assertEquals(200, send(client, "GET", path, null, AUTH.userToken(UUID.randomUUID(), "PLAYER", "MODERATOR")).statusCode());
            assertEquals(401, send(client, "GET", path, null, null).statusCode());
        }
    }

    @Test void identityIsTheAuthSubjectAndPrivilegedRolesGrantNoExtraReviewPowers() throws Exception {
        UUID author = UUID.randomUUID();
        UUID game = UUID.randomUUID();
        String authorToken = AUTH.userToken(author, "PLAYER");
        try (var client = HttpClient.newHttpClient()) {
            assertEquals(200, send(client, "PUT", "/api/v1/review/games/" + game + "/rating", "{\"rating\":8}", authorToken).statusCode());
            assertEquals(1L, jdbc.sql("SELECT count(*) FROM game_ratings WHERE user_id = ? AND game_id = ?").param(author).param(game)
                    .query(Long.class).single());
            // Gövdede sahte kimlik alanı kabul edilmez.
            assertEquals(400, send(client, "POST", "/api/v1/review/games/" + game + "/reviews",
                    "{\"body\":\"Metin\",\"userId\":\"" + UUID.randomUUID() + "\"}", authorToken).statusCode());
            var created = send(client, "POST", "/api/v1/review/games/" + game + "/reviews", "{\"body\":\"Auth token deneyimi\"}", authorToken);
            assertEquals(201, created.statusCode());
            assertEquals(author, jdbc.sql("SELECT user_id FROM reviews WHERE game_id = ?").param(game).query(UUID.class).single());
            String review = created.headers().firstValue("Location").orElseThrow();
            for (String role : List.of("MODERATOR", "ADMIN")) {
                String privileged = AUTH.userToken(UUID.randomUUID(), role);
                assertEquals(403, send(client, "PATCH", review, "{\"version\":0,\"body\":\"Başkası\"}", privileged).statusCode(), role);
                assertEquals(403, send(client, "DELETE", review + "?version=0", null, privileged).statusCode(), role);
            }
            assertEquals(200, send(client, "PATCH", review, "{\"version\":0,\"body\":\"Sahibi\"}", authorToken).statusCode());
        }
    }

    @Test void authServiceTokenIsNeverAUserTokenAndAUserTokenIsNeverAServiceToken() throws Exception {
        UUID account = UUID.randomUUID();
        String service = AUTH.serviceToken("review-service", "account:erase", account);
        String path = "/api/v1/review/games/" + UUID.randomUUID();
        try (var client = HttpClient.newHttpClient()) {
            assertEquals(401, send(client, "GET", path + "/statistics", null, service).statusCode());
            assertEquals(401, send(client, "PUT", path + "/rating", "{\"rating\":5}", service).statusCode());
            assertEquals(401, send(client, "POST", path + "/reviews", "{\"body\":\"Servis\"}", service).statusCode());
            for (String role : List.of("PLAYER", "MODERATOR", "ADMIN"))
                assertEquals(401, send(client, "DELETE", "/internal/v1/accounts/" + account, null, AUTH.userToken(account, role)).statusCode(), role);
            // Başka hedef servis için üretilmiş (doğru Auth imzalı) servis token'ı Review'da geçmez.
            assertEquals(401, send(client, "DELETE", "/internal/v1/accounts/" + account, null,
                    AUTH.serviceToken("game-service", "account:erase", account)).statusCode());
            // Doğru hedef ve kapsam, ancak başka hesaba bağlı token yoldaki hesabı silemez.
            assertEquals(403, send(client, "DELETE", "/internal/v1/accounts/" + account, null,
                    AUTH.serviceToken("review-service", "account:erase", UUID.randomUUID())).statusCode());
            // Doğru Auth imzası ve hedef, ancak silme kapsamı olmayan servis token'ı (ör. events:ingest) silemez.
            assertEquals(403, send(client, "DELETE", "/internal/v1/accounts/" + account, null,
                    AUTH.serviceToken("review-service", "events:ingest", account)).statusCode());
        }
    }

    @Test void authHttpEraserErasesOnlyTheBoundAccountIsIdempotentAndBlocksOldTokensWithoutLeavingCounters() throws Exception {
        UUID erased = UUID.randomUUID();
        UUID kept = UUID.randomUUID();
        UUID game = UUID.randomUUID();
        String erasedToken = AUTH.userToken(erased, "PLAYER");
        String keptToken = AUTH.userToken(kept, "PLAYER");
        URI base = URI.create("http://127.0.0.1:" + port);
        String keptReview;
        try (var client = HttpClient.newHttpClient()) {
            keptReview = send(client, "POST", "/api/v1/review/games/" + game + "/reviews", "{\"body\":\"Kalacak\"}", keptToken)
                    .headers().firstValue("Location").orElseThrow();
            assertEquals(200, send(client, "PUT", "/api/v1/review/games/" + game + "/rating", "{\"rating\":6}", keptToken).statusCode());
            var erasedReview = send(client, "POST", "/api/v1/review/games/" + UUID.randomUUID() + "/reviews", "{\"body\":\"Silinecek\"}", erasedToken);
            assertEquals(201, erasedReview.statusCode());
            assertEquals(200, send(client, "PUT", "/api/v1/review/games/" + game + "/rating", "{\"rating\":9}", erasedToken).statusCode());
            assertEquals(200, send(client, "PUT", "/api/v1/review/games/" + game + "/recommendation", "{\"recommended\":true}", erasedToken).statusCode());
            assertEquals(200, send(client, "PUT", keptReview + "/like", null, erasedToken).statusCode());
            assertEquals(200, send(client, "PUT", erasedReview.headers().firstValue("Location").orElseThrow() + "/like", null, keptToken).statusCode());
            assertTrue(counterRows(erased) > 0, "Silmeden önce kota sayaçları yazılmış olmalı");

            AUTH.erase("review", base, erased);

            assertEquals(0L, owned("game_ratings", erased) + owned("reviews", erased) + owned("game_recommendations", erased)
                    + owned("review_likes", erased) + counterRows(erased));
            assertEquals(1L, owned("game_ratings", kept));
            assertEquals(1L, owned("reviews", kept));
            assertEquals(0L, jdbc.sql("SELECT count(*) FROM review_likes WHERE user_id = ?").param(kept).query(Long.class).single(),
                    "Silinen yazarın incelemesine verilen beğeni de silinir");
            assertTrue(counterRows(kept) > 0, "Başka hesabın kotası korunur");
            AUTH.erase("review", base, erased); // idempotent: ikinci çağrı da onay (204)
            AUTH.erase("review", base, UUID.randomUUID()); // verisi olmayan hesap da onay

            // Silme sonrası eski token ile veri yazılamaz ve kota satırı yeniden oluşmaz.
            assertEquals(403, send(client, "PUT", "/api/v1/review/games/" + game + "/rating", "{\"rating\":9}", erasedToken).statusCode());
            assertEquals(403, send(client, "POST", "/api/v1/review/games/" + UUID.randomUUID() + "/reviews", "{\"body\":\"Yeniden\"}", erasedToken).statusCode());
            assertEquals(403, send(client, "PUT", keptReview + "/like", null, erasedToken).statusCode());
            assertEquals(0L, counterRows(erased));
            assertEquals(200, send(client, "GET", "/api/v1/review/games/" + game + "/statistics", null, keptToken).statusCode());
        }
    }

    @Test void authEraserWithAnotherServiceAudienceIsNotConfirmedAndLeavesDataUntouched() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID game = UUID.randomUUID();
        try (var client = HttpClient.newHttpClient()) {
            assertEquals(200, send(client, "PUT", "/api/v1/review/games/" + game + "/rating", "{\"rating\":4}", AUTH.userToken(owner, "PLAYER")).statusCode());
        }
        // Auth "game" hedefi için aud=game-service token'ı üretir; adres Review'a yönlendirilse bile Review onaylamamalı.
        URI review = URI.create("http://127.0.0.1:" + port);
        var failure = assertThrows(IllegalStateException.class, () -> AUTH.erase("game", review, owner));
        assertTrue(failure.getMessage().contains("SERVICE_ERASURE_HTTP_401"), failure.getMessage());
        assertFalse(failure.getMessage().contains("Bearer") || failure.getMessage().contains("eyJ"));
        assertEquals(1L, owned("game_ratings", owner));
    }

    long owned(String table, UUID user) {
        return jdbc.sql("SELECT count(*) FROM " + table + " WHERE user_id = ?").param(user).query(Long.class).single();
    }

    long counterRows(UUID account) {
        return jdbc.sql("SELECT count(*) FROM review_rate_limits WHERE account_hash = ?").param(AccountWriteLock.hash(account)).query(Long.class).single();
    }

    HttpResponse<String> send(HttpClient client, String method, String path, String body, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(path.startsWith("http") ? path : "http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    static ProductionAuthSupport auth() {
        try { return new ProductionAuthSupport(Files.createTempDirectory("auth-classes"), KEYS); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }

    static KeyPair keys() {
        try { var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(3072); return generator.generateKeyPair(); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }

    static HttpServer jwks() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            byte[] body = AUTH.jwks().getBytes(StandardCharsets.UTF_8);
            server.createContext("/jwks", exchange -> {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var output = exchange.getResponseBody()) { output.write(body); }
                exchange.close();
            });
            server.start();
            return server;
        } catch (Exception error) { throw new IllegalStateException(error); }
    }
}

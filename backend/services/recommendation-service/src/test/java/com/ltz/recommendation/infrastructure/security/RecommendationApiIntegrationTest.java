package com.ltz.recommendation.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ltz.recommendation.infrastructure.persistence.DisposableDatabaseInitializer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Gerçek Auth issuer/JWKS + Recommendation HTTP/DB; kaynak servisler kontrollü contract fixture'ıdır. */
@SpringBootTest
@ContextConfiguration(initializers = DisposableDatabaseInitializer.class)
class RecommendationApiIntegrationTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID GAME = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID OWNED = UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000103");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static HttpServer server;
    private static ProductionAuthSupport auth;
    private static volatile boolean consent = true;
    private static volatile String fault = "";
    private static final List<String> requests = java.util.Collections.synchronizedList(new ArrayList<>());
    private static java.util.concurrent.ExecutorService sourceExecutor;
    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    private MockMvc mvc;
    private String tokenA;
    private String tokenB;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        sourceExecutor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(sourceExecutor);
        server.createContext("/", RecommendationApiIntegrationTest::source);
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        registry.add("ltz.recommendation.auth.service-url", () -> base);
        registry.add("USER_SERVICE_URL", () -> base);
        registry.add("GAME_SERVICE_URL", () -> base);
        registry.add("LIBRARY_SERVICE_URL", () -> base);
        registry.add("ltz.recommendation.client-secret", () -> "test-only-service-credential-not-real");
        registry.add("ltz.recommendation.auth.read-timeout", () -> "150ms");
    }

    @BeforeAll
    static void startAuth() throws Exception { auth = new ProductionAuthSupport(Files.createTempDirectory("recommendation-auth-")); }

    @AfterAll
    static void stop() throws Exception {
        if (server != null) server.stop(0);
        if (sourceExecutor != null) sourceExecutor.close();
        if (auth != null) auth.close();
    }

    @BeforeEach
    void setup() throws Exception {
        jdbc.update("DELETE FROM recommendation_runs");
        jdbc.update("DELETE FROM recommendation_observations");
        context.getBean(com.ltz.recommendation.application.service.RecommendationRateLimiter.class).erase(A);
        context.getBean(com.ltz.recommendation.application.service.RecommendationRateLimiter.class).erase(B);
        consent = true;
        fault = "";
        requests.clear();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        tokenA = auth.token(A, "PLAYER");
        tokenB = auth.token(B, "PLAYER");
    }

    @Test
    void realAuthTokensProduceDifferentGenreResultsAndBoundedContractCalls() throws Exception {
        JsonNode first = generate(tokenA, "{}");
        assertThat(first.path("status").asString()).isEqualTo("PARTIAL");
        assertThat(first.path("items").size()).isEqualTo(1);
        JsonNode item = first.path("items").get(0);
        assertThat(item.path("gameId").asString()).isEqualTo(GAME.toString());
        assertThat(item.path("score").asInt()).isEqualTo(2000);
        assertThat(item.path("contribution").asInt()).isEqualTo(item.path("score").asInt());
        assertThat(item.path("matchedGenres").get(0).asString()).isEqualTo("rpg");
        JsonNode other = generate(tokenB, "{}");
        assertThat(other.path("items").get(0).path("gameId").asString()).isEqualTo(OTHER.toString());
        assertThat(requests.stream().filter(path -> path.contains("/metadata")).count()).isZero();
        assertThat(requests.size()).isEqualTo(16);
    }

    @Test
    void filtersColdStartMissingMetadataAndNoSignalDoNotInventRecommendations() throws Exception {
        assertThat(generate(tokenA, "{\"genre\":\"strategy\"}").path("items").size()).isZero();
        assertThat(generate(tokenA, "{\"platform\":\"linux\",\"size\":1}").path("items").size()).isEqualTo(1);
        assertThat(requests.stream().anyMatch(path -> path.contains("platform=linux"))).isTrue();
        fault = "empty-preferences";
        assertThat(generate(tokenA, "{}").path("items").size()).isZero();
        fault = "unknown-adult";
        assertThat(generate(tokenA, "{}").path("items").size()).isZero();
    }

    @Test
    void historyAndFeedbackAreOwnerOnlyVersionedAndIdempotent() throws Exception {
        JsonNode run = generate(tokenA, "{}");
        String runId = run.path("runId").asString();
        String itemId = run.path("items").get(0).path("itemId").asString();
        String path = "/api/v1/recommendation/runs/" + runId;
        request(get(path), tokenA, 200);
        request(get(path), tokenB, 404);
        String feedback = path + "/items/" + itemId + "/feedback";
        assertThat(request(get(feedback), tokenA, 200).path("version").asLong()).isZero();
        request(put(feedback).content("{\"feedback\":\"LIKE\",\"version\":0}"), tokenB, 404);
        JsonNode saved = request(put(feedback).content("{\"feedback\":\"LIKE\",\"version\":0}"), tokenA, 200);
        assertThat(saved.path("version").asLong()).isEqualTo(1);
        assertThat(request(put(feedback).content("{\"feedback\":\"LIKE\",\"version\":0}"), tokenA, 200)).isEqualTo(saved);
        request(put(feedback).content("{\"feedback\":\"NOT_INTERESTED\",\"version\":0}"), tokenA, 409);
        request(put(feedback).content("{\"feedback\":\"NOT_INTERESTED\",\"version\":1}"), tokenA, 200);
        assertThat(generate(tokenA, "{}").path("items").size()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM recommendation_feedback", Integer.class)).isEqualTo(1);
        assertThat(request(get("/api/v1/recommendation/runs"), tokenB, 200).path("items").size()).isZero();
    }

    @Test
    void cursorIsOwnerScopedAndDoesNotShiftWhenANewRunIsInserted() throws Exception {
        JsonNode oldest = generate(tokenA, "{}");
        generate(tokenA, "{}");
        JsonNode page = request(get("/api/v1/recommendation/runs?size=1"), tokenA, 200);
        String cursor = page.path("nextCursor").asString();
        generate(tokenA, "{}");
        JsonNode next = request(get("/api/v1/recommendation/runs?size=1&beforeRunId=" + cursor), tokenA, 200);
        assertThat(next.path("items").get(0).path("runId").asString()).isEqualTo(oldest.path("runId").asString());
        request(get("/api/v1/recommendation/runs?beforeRunId=" + cursor), tokenB, 404);
    }

    @Test
    void consentDependencyErrorsMalformedDataRedirectAndTimeoutFailClosed() throws Exception {
        consent = false;
        request(post("/api/v1/recommendation/games").content("{}"), tokenA, 403);
        consent = true;
        fault = "account-inactive";
        request(post("/api/v1/recommendation/games").content("{}"), tokenA, 403);
        for (String mode : List.of("unavailable", "malformed", "empty-body", "array-body",
                "redirect", "timeout", "library-overflow", "wrong-owner")) {
            fault = mode;
            request(post("/api/v1/recommendation/games").content("{}"), tokenA, 503);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM recommendation_runs", Integer.class)).isZero();
    }

    @Test
    void validationBodyLimitRoleAndTokenBoundariesAreEnforced() throws Exception {
        assertThat(context.getBeansOfType(org.springframework.security.core.userdetails.UserDetailsService.class)).isEmpty();
        mvc.perform(get("/api/v1/recommendation/runs")).andExpect(status().isUnauthorized());
        request(get("/api/v1/recommendation/runs"), "not-a-jwt", 401);
        String[] parts = tokenA.split("\\.");
        String wrongSignature = (parts[2].startsWith("A") ? "B" : "A") + parts[2].substring(1);
        request(get("/api/v1/recommendation/runs"), parts[0] + "." + parts[1] + "." + wrongSignature, 401);
        request(get("/api/v1/recommendation/runs"), auth.serviceToken(A), 401);
        var noRole = new com.nimbusds.jwt.SignedJWT(com.nimbusds.jwt.SignedJWT.parse(tokenA).getHeader(),
                new com.nimbusds.jwt.JWTClaimsSet.Builder(com.nimbusds.jwt.SignedJWT.parse(tokenA).getJWTClaimsSet())
                        .claim("roles", List.of()).build());
        noRole.sign(new com.nimbusds.jose.crypto.RSASSASigner(auth.key()));
        request(get("/api/v1/recommendation/runs"), noRole.serialize(), 403);
        for (String body : List.of("{\"size\":0}", "{\"size\":51}", "{\"genre\":\" \"}",
                "{\"systemCompatible\":true}", "{\"userId\":\"" + B + "\"}")) {
            request(post("/api/v1/recommendation/games").content(body), tokenA, 400);
        }
        request(post("/api/v1/recommendation/games").content("x".repeat(16385)), tokenA, 413);
        request(get("/api/v1/recommendation/runs?size=21"), tokenA, 400);
        request(get("/api/v1/recommendation/runs/not-uuid"), tokenA, 400);
    }

    @Test
    void accountErasureHasRealD4BindingIsIdempotentAndCascades() throws Exception {
        JsonNode run = generate(tokenA, "{}");
        String path = "/api/v1/recommendation/runs/" + run.path("runId").asString() + "/items/"
                + run.path("items").get(0).path("itemId").asString() + "/feedback";
        request(put(path).content("{\"feedback\":\"LIKE\",\"version\":0}"), tokenA, 200);
        request(delete("/internal/v1/accounts/" + A), tokenA, 401);
        request(delete("/internal/v1/accounts/" + B), auth.serviceToken(A), 403);
        request(delete("/internal/v1/accounts/" + A), auth.serviceToken(A), 204);
        request(delete("/internal/v1/accounts/" + A), auth.serviceToken(A), 204);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM recommendation_items", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM recommendation_feedback", Integer.class)).isZero();
    }

    @Test
    void generationRateLimitAndRetentionAreEnforced() throws Exception {
        for (int index = 0; index < 10; index++) generate(tokenA, "{}");
        request(post("/api/v1/recommendation/games").content("{}"), tokenA, 429);
        jdbc.update("UPDATE recommendation_runs SET created_at=now()-interval '31 days',expires_at=now()-interval '1 day'");
        assertThat(request(get("/api/v1/recommendation/runs"), tokenA, 200).path("items").size()).isZero();
        context.getBean(com.ltz.recommendation.infrastructure.persistence.repository.PostgresRecommendationHistory.class).purgeExpired();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM recommendation_runs", Integer.class)).isZero();
    }

    @Test
    void withdrawalPurgesHistoryAndConcurrentFeedbackRemainsSingleRow() throws Exception {
        JsonNode run = generate(tokenA, "{}");
        UUID runId = UUID.fromString(run.path("runId").asString());
        UUID itemId = UUID.fromString(run.path("items").get(0).path("itemId").asString());
        var service = context.getBean(com.ltz.recommendation.application.service.PersonalizedRecommendationService.class);
        var actor = new com.ltz.recommendation.application.port.PersonalizationSource.Actor(A, "Bearer " + tokenA);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(4)) {
            List<java.util.concurrent.Callable<Long>> tasks = new ArrayList<>();
            for (int index = 0; index < 12; index++) tasks.add(() -> service.feedback(actor, runId, itemId,
                    com.ltz.recommendation.application.port.RecommendationHistory.FeedbackValue.LIKE, 0).version());
            for (var result : executor.invokeAll(tasks)) assertThat(result.get()).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM recommendation_feedback", Integer.class)).isEqualTo(1);
        consent = false;
        request(get("/api/v1/recommendation/runs/" + runId), tokenA, 403);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM recommendation_runs", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM recommendation_feedback", Integer.class)).isZero();
    }

    @Test
    void databaseConstraintsRejectDuplicateAndInvalidFeedbackAndRanks() throws Exception {
        generate(tokenA, "{}");
        for (String update : List.of("UPDATE recommendation_items SET score=0", "UPDATE recommendation_items SET rank=51",
                "UPDATE recommendation_items SET matched_genres=ARRAY[]::varchar[]",
                "INSERT INTO recommendation_items SELECT * FROM recommendation_items")) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(update))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        }
    }

    @Test
    void quotaFailureRollsBackRunAndObservationAtomically() throws Exception {
        jdbc.update("""
                INSERT INTO recommendation_runs(id,user_id,algorithm_version,result_limit,created_at,expires_at)
                SELECT gen_random_uuid(),?,'rules-v1',20,now(),now()+interval '30 days' FROM generate_series(1,500)
                """, A);
        request(post("/api/v1/recommendation/games").content("{}"), tokenA, 429);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM recommendation_runs", Integer.class)).isEqualTo(500);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM recommendation_observations", Integer.class)).isZero();
    }

    @Test
    void consentDenialsCannotResetTheGenerationQuota() throws Exception {
        consent = false;
        for (int index = 0; index < 10; index++) request(post("/api/v1/recommendation/games").content("{}"), tokenA, 403);
        request(post("/api/v1/recommendation/games").content("{}"), tokenA, 429);
    }

    private JsonNode generate(String token, String body) throws Exception {
        return request(post("/api/v1/recommendation/games").content(body), token, 200);
    }
    private JsonNode request(MockHttpServletRequestBuilder request, String token, int expected) throws Exception {
        var response = mvc.perform(request.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token)).andExpect(status().is(expected)).andReturn().getResponse();
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
        return response.getContentAsByteArray().length == 0 ? JSON.nullNode() : JSON.readTree(response.getContentAsByteArray());
    }

    private static void source(HttpExchange exchange) throws java.io.IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            if (path.contains(".well-known")) { reply(exchange, 200, auth.jwks()); return; }
            requests.add(exchange.getRequestURI().toString());
            String bearer = exchange.getRequestHeaders().getFirst("Authorization");
            var jwt = bearer == null ? null : com.nimbusds.jwt.SignedJWT.parse(bearer.substring(7)).getJWTClaimsSet();
            if (path.equals("/api/v1/user/me/consents")) { reply(exchange, 200, "{\"recommendation\":" + consent + "}"); return; }
            if (path.equals("/api/v1/user/me")) {
                UUID id = UUID.fromString(jwt.getSubject());
                reply(exchange, 200, "{\"userId\":\"" + (fault.equals("wrong-owner") ? B : id) + "\",\"favoriteGenres\":"
                        + (fault.equals("empty-preferences") ? "[]" : id.equals(A) ? "[\"rpg\"]" : "[\"strategy\"]") + "}");
                return;
            }
            if (path.equals("/internal/v1/service-tokens")) {
                if (fault.equals("account-inactive")) { reply(exchange, 403, "{}"); return; }
                var body = JSON.readTree(exchange.getRequestBody().readAllBytes());
                assertThat(body.path("clientId").asString()).isEqualTo("recommendation-service");
                assertThat(body.path("audience").asString()).isEqualTo("library-service");
                reply(exchange, 200, "{\"accessToken\":\"" + auth.libraryToken(UUID.fromString(body.path("accountId").asString()))
                        + "\",\"tokenType\":\"Bearer\",\"expiresIn\":60,\"scope\":\"signals:read\"}");
                return;
            }
            if (path.contains("recommendation-signals")) {
                assertThat(jwt.getAudience()).containsExactly("library-service");
                assertThat(jwt.getSubject()).isEqualTo("svc:recommendation-service");
                assertThat(path).contains(jwt.getStringClaim("account_id"));
                reply(exchange, 200, "{\"items\":[{\"gameId\":\"" + OWNED + "\",\"favorite\":true,\"completed\":true}],"
                        + "\"page\":0,\"size\":50,\"totalElements\":" + (fault.equals("library-overflow") ? 501 : 1) + "}");
                return;
            }
            gameReply(exchange);
        } catch (Exception exception) { if (exchange.getResponseCode() == -1) reply(exchange, 500, "{}"); }
        finally { exchange.close(); }
    }

    private static void gameReply(HttpExchange exchange) throws Exception {
        if (fault.equals("unavailable")) { reply(exchange, 503, "{}"); return; }
        if (fault.equals("malformed")) { reply(exchange, 200, "{broken"); return; }
        if (fault.equals("empty-body")) { reply(exchange, 200, ""); return; }
        if (fault.equals("array-body")) { reply(exchange, 200, "[]"); return; }
        if (fault.equals("redirect")) { exchange.getResponseHeaders().add("Location", "/forbidden"); reply(exchange, 302, "{}"); return; }
        if (fault.equals("timeout")) { Thread.sleep(400); reply(exchange, 200, "{}"); return; }
        boolean strategy = exchange.getRequestURI().getQuery().contains("genre=strategy");
        String adult = fault.equals("unknown-adult") ? "null" : "false";
        String game = "{\"game\":{\"gameId\":\"%s\",\"gameType\":\"MAIN_GAME\"},\"adult\":%s}";
        reply(exchange, 200, "{\"page\":0,\"items\":[" + game.formatted(strategy ? OTHER : GAME, adult)
                + "," + game.formatted(OWNED, "false") + "],\"includeAdultApplied\":false}");
    }
    private static void reply(HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}

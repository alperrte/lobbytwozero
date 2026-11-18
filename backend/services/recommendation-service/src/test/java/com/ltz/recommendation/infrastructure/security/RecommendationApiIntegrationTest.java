package com.ltz.recommendation.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ltz.recommendation.application.service.FixedRateLimiterConfiguration;
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
import org.springframework.context.annotation.Import;
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
@Import(FixedRateLimiterConfiguration.class)
class RecommendationApiIntegrationTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID GAME = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID OWNED = UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000103");
    private static final UUID C1 = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID C2 = UUID.fromString("00000000-0000-0000-0000-000000000202");
    private static final UUID C3 = UUID.fromString("00000000-0000-0000-0000-000000000203");
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
        registry.add("REVIEW_SERVICE_URL", () -> base);
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
        // OWNED (favori+tamamlanan, rpg): GENRE 20% x10000 + FAVORITE 40% x10000 + COMPLETED 10% x10000 = 7000
        assertThat(item.path("score").asInt()).isEqualTo(7000);
        assertThat(first.path("limitations").toString()).doesNotContain("SIMILARITY_UNAVAILABLE");
        assertThat(item.path("contribution").asInt()).isEqualTo(item.path("score").asInt());
        assertThat(item.path("matchedGenres").get(0).asString()).isEqualTo("rpg");
        JsonNode other = generate(tokenB, "{}");
        assertThat(other.path("items").get(0).path("gameId").asString()).isEqualTo(OTHER.toString());
        assertThat(requests.stream().filter(path -> path.contains("/metadata")).count()).isZero();
        assertThat(requests.size()).isEqualTo(22); // +3 üretim başına: ikinci Library okuması, servis token'ı, Game tür batch'i (NOT VERIFIED: PostgreSQL yok)
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

    @Test
    void similarPlayersUseOnlySharedSignalsAndBoundedUserCalls() throws Exception {
        JsonNode result = request(get("/api/v1/recommendation/players"), tokenA, 200);
        assertThat(result.path("kind").asString()).isEqualTo("SIMILAR_PLAYERS");
        assertThat(result.path("algorithmVersion").asString()).isEqualTo("player-affinity-v1");
        // C1 ve C2 ortak "rpg" türüne sahip; C3 (racing) ortak tür olmadığı için elenir.
        assertThat(result.path("items").size()).isEqualTo(2);
        JsonNode top = result.path("items").get(0);
        // C2: tür 1/1*60 + platform 0 + tip 1/1*20 = 6000 + 0 + 2000
        assertThat(top.path("userId").asString()).isEqualTo(C2.toString());
        assertThat(top.path("sharedGenres").get(0).asString()).isEqualTo("rpg");
        assertThat(top.path("score").asInt()).isEqualTo(8000);
        // C1: tür 1/2*60 + platform 1/1*20 + tip 1/1*20 = 3000 + 2000 + 2000
        assertThat(result.path("items").get(1).path("userId").asString()).isEqualTo(C1.toString());
        assertThat(result.path("items").get(1).path("score").asInt()).isEqualTo(7000);
        // Yalnız kimlik, skor ve ORTAK sinyal döner; adayın ham tercihleri veya viewer dışı alan dönmez.
        assertThat(top.propertyNames()).containsExactlyInAnyOrder("userId", "score", "sharedGenres", "sharedPlatforms", "sharedPlayerTypes");
        assertThat(requests.stream().filter(path -> path.contains("recommendation-candidates")).toList())
                .singleElement().satisfies(path -> assertThat(path).contains("excludeFriends=true").contains("limit=50"));
        assertThat(request(get("/api/v1/recommendation/players").param("size", "1"), tokenA, 200).path("items").size()).isEqualTo(1);
        assertThat(request(get("/api/v1/recommendation/players").param("genre", "rpg").param("platform", "pc"), tokenA, 200)
                .path("items").size()).isEqualTo(2);
        assertThat(requests.stream().anyMatch(path -> path.contains("genre=rpg") && path.contains("platform=pc"))).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM recommendation_runs", Integer.class)).isZero();
    }

    @Test
    void friendSuggestionsRequireSharedGenreAndPlatformAndNeverIncludeFriends() throws Exception {
        JsonNode result = request(get("/api/v1/recommendation/friends"), tokenA, 200);
        assertThat(result.path("kind").asString()).isEqualTo("FRIEND_SUGGESTIONS");
        // C2 ortak platform yok -> elenir; C1 tür 1/2*40 + platform 1*40 + tip 1*20 = 2000 + 4000 + 2000
        assertThat(result.path("items").size()).isEqualTo(1);
        assertThat(result.path("items").get(0).path("userId").asString()).isEqualTo(C1.toString());
        assertThat(result.path("items").get(0).path("score").asInt()).isEqualTo(8000);
        assertThat(requests.stream().anyMatch(path -> path.contains("recommendation-candidates") && path.contains("excludeFriends=true"))).isTrue();
        // Kullanıcı B (strategy) için C1 ortak tür "strategy" taşır.
        assertThat(request(get("/api/v1/recommendation/friends"), tokenB, 200).path("items").get(0).path("sharedGenres").get(0).asString())
                .isEqualTo("strategy");
    }

    @Test
    void playerRecommendationsFailClosedOnConsentMissingPreferencesAndBrokenSources() throws Exception {
        consent = false;
        request(get("/api/v1/recommendation/players"), tokenA, 403);
        request(get("/api/v1/recommendation/friends"), tokenA, 403);
        consent = true;
        requests.clear();
        fault = "empty-preferences";
        JsonNode empty = request(get("/api/v1/recommendation/players"), tokenA, 200);
        assertThat(empty.path("items").size()).isZero();
        assertThat(empty.path("limitations").get(0).asString()).isEqualTo("VIEWER_PREFERENCES_MISSING");
        assertThat(requests.stream().noneMatch(path -> path.contains("recommendation-candidates"))).isTrue();
        for (String broken : List.of("candidates-unavailable", "candidates-self")) {
            fault = broken;
            request(get("/api/v1/recommendation/players"), tokenA, 503);
        }
        fault = "wrong-owner";
        request(get("/api/v1/recommendation/friends"), tokenA, 503);
    }

    @Test
    void playerRecommendationsValidateInputAndReportTruncatedScan() throws Exception {
        for (String size : List.of("0", "21", "-1")) request(get("/api/v1/recommendation/players").param("size", size), tokenA, 400);
        request(get("/api/v1/recommendation/players").param("size", "abc"), tokenA, 400);
        request(get("/api/v1/recommendation/friends").param("genre", " "), tokenA, 400);
        // İstemci başka kullanıcı/dahil-et bayrağı veremez: bilinmeyen parametre sonucu değiştirmez.
        request(get("/api/v1/recommendation/players").param("userId", B.toString()).param("excludeFriends", "false")
                .header("X-User-Id", B.toString()), tokenA, 200);
        assertThat(requests.stream().filter(path -> path.contains("recommendation-candidates"))
                .allMatch(path -> path.contains("excludeFriends=true") && !path.contains(B.toString()))).isTrue();
        fault = "candidates-truncated";
        requests.clear();
        JsonNode truncated = request(get("/api/v1/recommendation/players").param("size", "20"), tokenA, 200);
        assertThat(truncated.path("limitations").get(0).asString()).isEqualTo("CANDIDATE_SCAN_TRUNCATED");
        assertThat(requests.stream().filter(path -> path.contains("recommendation-candidates")).count()).isEqualTo(4);
        assertThat(truncated.path("items").size()).isEqualTo(4);
    }

    @Test
    void playerRecommendationsRequireUserTokenAndShareGenerationQuota() throws Exception {
        for (String segment : List.of("players", "friends")) {
            String path = "/api/v1/recommendation/" + segment;
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            request(get(path), auth.serviceToken(A), 401);
        }
        for (int index = 0; index < 10; index++) request(get("/api/v1/recommendation/players"), tokenA, 200);
        request(get("/api/v1/recommendation/friends"), tokenA, 429);
    }

    @Test
    void hardwareCompatibleGamesRequireUserTokenAndRoleBeforeAnySourceCall() throws Exception {
        String path = "/api/v1/recommendation/games/hardware-compatible";
        mvc.perform(get(path).param("profileId", UUID.randomUUID().toString()).param("platform", "windows"))
                .andExpect(status().isUnauthorized());
        request(get(path).param("profileId", UUID.randomUUID().toString()).param("platform", "windows"), auth.serviceToken(A), 401);
        // Geçerli kullanıcı, ama eksik/bozuk parametre kaynak çağrısından önce 400 olur.
        request(get(path).param("platform", "windows"), tokenA, 400);
        request(get(path).param("profileId", "x").param("platform", "windows"), tokenA, 400);
        assertThat(requests).isEmpty();
    }

    @Test
    void unavailableEventRecommendationsRemainClosed() throws Exception {
        assertUnavailableRecommendationBoundary("events");
    }

    /** Bu yollar negatif probe'dur; uygulanmış veya vaat edilmiş endpoint sözleşmesi değildir. */
    private void assertUnavailableRecommendationBoundary(String segment) throws Exception {
        String path = "/api/v1/recommendation/" + segment;
        String payload = "{\"userId\":\"" + B + "\",\"candidateIds\":[\"" + B + "\"],\"includePrivate\":true}";
        var anonymous = mvc.perform(get(path)).andExpect(status().isUnauthorized()).andReturn().getResponse();
        assertThat(anonymous.getHeader("Cache-Control")).contains("no-store");
        // Bearer/CSRF taşımayan POST, mevcut CSRF filtresinde erişim kontrolünden önce reddedilir.
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isForbidden());
        for (String token : List.of(tokenA, tokenB, auth.token(A, "ADMIN"))) {
            assertDeniedWithoutCandidateData(request(get(path).param("userId", B.toString())
                    .header("X-User-Id", B.toString()).header("X-Role", "ADMIN"), token, 403));
            assertDeniedWithoutCandidateData(request(post(path).content(payload), token, 403));
        }
        request(get(path), auth.serviceToken(A), 401);
        request(post(path).content(payload), auth.serviceToken(A), 401);
        assertThat(requests).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM recommendation_runs", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM recommendation_observations", Integer.class)).isZero();
    }

    private static void assertDeniedWithoutCandidateData(JsonNode response) {
        assertThat(response.path("code").asString()).isEqualTo("ACCESS_DENIED");
        assertThat(response.has("items")).isFalse();
        assertThat(response.has("candidateIds")).isFalse();
        assertThat(response.toString()).doesNotContain(B.toString());
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
                        + (fault.equals("empty-preferences") ? "[]" : id.equals(A) ? "[\"rpg\"]" : "[\"strategy\"]")
                        + ",\"favoritePlatforms\":[\"pc\"],\"playerTypes\":[\"casual\"]}");
                return;
            }
            if (path.equals("/internal/v1/service-tokens")) {
                if (fault.equals("account-inactive")) { reply(exchange, 403, "{}"); return; }
                var body = JSON.readTree(exchange.getRequestBody().readAllBytes());
                assertThat(body.path("clientId").asString()).isEqualTo("recommendation-service");
                UUID account = UUID.fromString(body.path("accountId").asString());
                if (body.path("audience").asString().equals("user-service")) {
                    reply(exchange, 200, "{\"accessToken\":\"" + auth.candidatesToken(account)
                            + "\",\"tokenType\":\"Bearer\",\"expiresIn\":60,\"scope\":\"candidates:read\"}");
                    return;
                }
                assertThat(body.path("audience").asString()).isEqualTo("library-service");
                reply(exchange, 200, "{\"accessToken\":\"" + auth.libraryToken(account)
                        + "\",\"tokenType\":\"Bearer\",\"expiresIn\":60,\"scope\":\"signals:read\"}");
                return;
            }
            if (path.endsWith("/recommendation-candidates")) { candidates(exchange, jwt); return; }
            if (path.contains("recommendation-signals")) {
                assertThat(jwt.getAudience()).containsExactly("library-service");
                assertThat(jwt.getSubject()).isEqualTo("svc:recommendation-service");
                assertThat(path).contains(jwt.getStringClaim("account_id"));
                reply(exchange, 200, "{\"items\":[{\"gameId\":\"" + OWNED + "\",\"favorite\":true,\"completed\":true}],"
                        + "\"page\":0,\"size\":50,\"totalElements\":" + (fault.equals("library-overflow") ? 501 : 1) + "}");
                return;
            }
            if (path.equals("/api/v1/game/games/recommendation-metadata/batch")) {
                // REC-02/05: Library'deki favori/tamamlanan oyunların tür kodları (hepsi görünür, rpg).
                var ids = JSON.readTree(exchange.getRequestBody().readAllBytes()).path("gameIds");
                var items = new StringBuilder();
                for (var id : ids) {
                    if (!items.isEmpty()) items.append(',');
                    items.append("{\"gameId\":\"").append(id.asString()).append("\",\"adult\":false,\"visible\":true,")
                            .append("\"genres\":[{\"code\":\"RPG\"}]}");
                }
                reply(exchange, 200, "{\"items\":[" + items + "],\"unavailable\":[]}");
                return;
            }
            if (path.equals("/api/v1/review/ratings/mine")) {
                assertThat(jwt.getSubject()).isNotEqualTo("svc:recommendation-service");
                reply(exchange, 200, "{\"items\":[],\"nextCursor\":null,\"truncated\":false}");
                return;
            }
            gameReply(exchange);
        } catch (Exception exception) { if (exchange.getResponseCode() == -1) reply(exchange, 500, "{}"); }
        finally { exchange.close(); }
    }

    /** User aday ucu sözleşme fixture'ı: ortak tür/platform olan, olmayan ve (hata modunda) bozuk adaylar. */
    private static void candidates(HttpExchange exchange, com.nimbusds.jwt.JWTClaimsSet jwt) throws Exception {
        assertThat(jwt.getAudience()).containsExactly("user-service");
        assertThat(jwt.getSubject()).isEqualTo("svc:recommendation-service");
        assertThat(exchange.getRequestURI().getPath()).contains(jwt.getStringClaim("account_id"));
        if (fault.equals("candidates-unavailable")) { reply(exchange, 503, "{}"); return; }
        UUID viewer = UUID.fromString(jwt.getStringClaim("account_id"));
        if (fault.equals("candidates-self")) {
            reply(exchange, 200, "{\"candidates\":[" + candidateBody(viewer, "[\"rpg\"]", "[\"pc\"]") + "],\"nextCursor\":null}");
            return;
        }
        if (fault.equals("candidates-truncated")) {
            long calls = requests.stream().filter(path -> path.contains("recommendation-candidates")).count();
            reply(exchange, 200, "{\"candidates\":[" + candidateBody(new UUID(0, 0x300 + calls), "[\"rpg\"]", "[\"pc\"]")
                    + "],\"nextCursor\":\"" + new UUID(0, 0x900 + calls) + "\"}");
            return;
        }
        String first = candidateBody(C1, "[\"rpg\",\"strategy\"]", "[\"pc\"]");
        String second = candidateBody(C2, "[\"rpg\"]", "[\"console\"]");
        String third = candidateBody(C3, "[\"racing\"]", "[\"pc\"]");
        reply(exchange, 200, "{\"candidates\":[" + first + "," + second + "," + third + "],\"nextCursor\":null}");
    }

    private static String candidateBody(UUID id, String genres, String platforms) {
        return "{\"userId\":\"" + id + "\",\"playerTypes\":[\"casual\"],\"favoriteGenres\":" + genres
                + ",\"favoritePlatforms\":" + platforms + "}";
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

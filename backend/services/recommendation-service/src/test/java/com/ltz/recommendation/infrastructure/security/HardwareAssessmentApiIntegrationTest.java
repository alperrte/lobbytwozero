package com.ltz.recommendation.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ltz.recommendation.application.service.RecommendationRateLimiter;
import com.ltz.recommendation.infrastructure.persistence.DisposableDatabaseInitializer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Gerçek Auth issuer/JWKS; Hardware/Game/Performance owner API'leri kontrollü contract fixture. */
@SpringBootTest
@ContextConfiguration(initializers = DisposableDatabaseInitializer.class)
class HardwareAssessmentApiIntegrationTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID PROFILE = UUID.fromString("00000000-0000-0000-0000-000000000050");
    private static final UUID GAME = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final String PATH = "/api/v1/recommendation/hardware/assessments";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final List<String> REQUESTS = Collections.synchronizedList(new ArrayList<>());
    private static volatile String mode = "";
    private static HttpServer server;
    private static ExecutorService executor;
    private static ProductionAuthSupport auth;
    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    private MockMvc mvc;
    private String token;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/", HardwareAssessmentApiIntegrationTest::handle);
        server.start();
        String origin = "http://127.0.0.1:" + server.getAddress().getPort();
        registry.add("ltz.recommendation.auth.service-url", () -> origin);
        for (String name : List.of("USER_SERVICE_URL", "HARDWARE_SERVICE_URL", "GAME_SERVICE_URL", "PERFORMANCE_SERVICE_URL")) {
            registry.add(name, () -> origin);
        }
        registry.add("ltz.recommendation.client-secret", () -> "test-only-recommendation-credential");
        registry.add("ltz.recommendation.auth.read-timeout", () -> "200ms");
        registry.add("ltz.recommendation.assistant.python-executable",
                com.ltz.recommendation.infrastructure.client.PythonTestRuntime::executable);
    }

    @BeforeAll
    static void auth() throws Exception { auth = new ProductionAuthSupport(Files.createTempDirectory("recommendation-hardware-auth-")); }
    @AfterAll
    static void close() throws Exception {
        if (server != null) server.stop(0);
        if (executor != null) executor.close();
        if (auth != null) auth.close();
    }

    @BeforeEach
    void setup() throws Exception {
        mode = "";
        REQUESTS.clear();
        context.getBean(RecommendationRateLimiter.class).erase(A);
        context.getBean(RecommendationRateLimiter.class).erase(B);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        token = auth.token(A, "PLAYER");
    }

    @Test
    void manualBrandlessOwnerProfileProducesSeparateLevelsAndConservativeAdviceWithoutPersistence() throws Exception {
        long before = jdbc.queryForObject("SELECT count(*) FROM recommendation_runs", Long.class);
        JsonNode body = assess(token, request("1920x1080", "HIGH"), 200);
        assertThat(body.path("hardwareProfileVersion").asLong()).isEqualTo(7);
        assertThat(body.path("minimum").path("computeStatus").asString()).isEqualTo("MEETS");
        assertThat(body.path("recommended").path("computeStatus").asString()).isEqualTo("BELOW");
        assertThat(body.path("suitability").asString()).isEqualTo("UNKNOWN");
        assertThat(body.path("graphics").path("suggestedPreset").asString()).isEqualTo("LOW");
        assertThat(body.path("upgrades").get(0).path("field").asString()).isEqualTo("RAM");
        assertThat(body.path("componentCompatibility").path("status").asString()).isEqualTo("UNKNOWN");
        assertThat(REQUESTS).hasSize(6);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM recommendation_runs", Long.class)).isEqualTo(before);
    }

    @Test
    void foreignProfileAndAnotherPrincipalCannotReachGameOrPerformance() throws Exception {
        assess(auth.token(B, "PLAYER"), request(null, null), 404);
        assertThat(REQUESTS).noneMatch(path -> path.contains("/metadata") || path.contains("performance-reference"));
        REQUESTS.clear();
        assess(token, request(null, null).replace(PROFILE.toString(), B.toString()), 404);
    }

    @Test
    void profileAndGameIdentityMismatchAndIncompletePageFailClosed() throws Exception {
        for (String fault : List.of("wrong-profile", "wrong-game", "paged-profile", "invalid-capacity", "duplicate-component")) {
            mode = fault;
            assess(token, request(null, null), 503);
        }
    }

    @Test
    void missingRequirementsOrComponentsRemainUnknownAndDoNotInventUpgrades() throws Exception {
        mode = "missing-requirements";
        var result = assess(token, request(null, null), 200);
        assertThat(result.path("minimum").path("computeStatus").asString()).isEqualTo("UNKNOWN");
        assertThat(result.path("graphics").path("suggestedPreset").isNull()).isTrue();
        assertThat(result.path("upgrades").size()).isZero();
        mode = "empty-profile";
        assertThat(assess(token, request(null, null), 200).path("suitability").asString()).isEqualTo("UNKNOWN");
    }

    @Test
    void lowRamAndStorageAreProvenDeficitsWithoutSpeculativeCpuGpuUpgrade() throws Exception {
        mode = "below-minimum";
        var body = assess(token, request(null, null), 200);
        assertThat(body.path("suitability").asString()).isEqualTo("BELOW_MINIMUM");
        assertThat(body.path("graphics").path("suggestedPreset").isNull()).isTrue();
        for (JsonNode upgrade : body.path("upgrades")) assertThat(upgrade.path("field").asString()).isIn("RAM", "STORAGE");
    }

    @Test
    void incompatibleSocketStopsGraphicsSuggestionDespiteMatchingGameModel() throws Exception {
        mode = "incompatible-socket";
        var body = assess(token, request(null, null), 200);
        assertThat(body.path("componentCompatibility").path("status").asString()).isEqualTo("INCOMPATIBLE");
        assertThat(body.path("graphics").path("suggestedPreset").isNull()).isTrue();
    }

    @Test
    void sparseReliableOutlierAndDifferentRequestedContextsNeverBecomeFpsPredictions() throws Exception {
        for (String fault : List.of("no-fps", "sparse-fps", "", "outlier-fps")) {
            mode = fault;
            for (String resolution : List.of("1920x1080", "3840x2160")) {
                var body = assess(token, request(resolution, "ULTRA"), 200).path("fps");
                assertThat(body.path("status").asString()).isEqualTo("INSUFFICIENT_DATA");
                assertThat(body.path("estimatedLowerFps").isNull()).isTrue();
                assertThat(body.path("estimatedUpperFps").isNull()).isTrue();
                assertThat(body.path("confidence").asString()).isEqualTo("NONE");
            }
        }
    }

    @Test
    void malformedFpsBindingCountsAndStatisticsAreDependencyErrors() throws Exception {
        for (String fault : List.of("wrong-fps-profile", "wrong-fps-game", "invalid-fps", "negative-count")) {
            mode = fault;
            assess(token, request(null, null), 503);
        }
    }

    @Test
    void downstream503TimeoutRedirectAndMalformedBodiesAreControlled() throws Exception {
        for (String fault : List.of("hardware-unavailable", "game-timeout", "game-redirect", "game-malformed", "performance-unavailable")) {
            mode = fault;
            assess(token, request(null, null), 503);
        }
        assertThat(REQUESTS).noneMatch(path -> path.contains("redirect-target"));
    }

    @Test
    void authenticationConsentValidationAndDataVisibilityAreEnforced() throws Exception {
        mvc.perform(post(PATH).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON).content(request(null, null))).andExpect(status().isUnauthorized());
        assess(auth.serviceToken(A), request(null, null), 401);
        mode = "no-consent";
        jdbc.update("INSERT INTO recommendation_runs(id,user_id,algorithm_version,result_limit,created_at,expires_at) "
                + "VALUES (?,?,'rules-v1',20,now(),now()+interval '30 days')", UUID.randomUUID(), A);
        assess(token, request(null, null), 403);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM recommendation_runs WHERE user_id=?", Integer.class, A)).isZero();
        mode = "adult";
        assess(token, request(null, null), 404);
        mode = "unknown-adult";
        assess(token, request(null, null), 404);
        for (String body : List.of("{}", request(null, null).replace("windows", " "), request("bad", "HIGH"),
                request("1920x1080", "FAKE"), request(null, null).replace("\"platform\"", "\"userId\""))) {
            assess(token, body, 400);
        }
    }

    @Test
    void assistantGameAndUsageRunPythonWithoutPrivateContextOrHistoryWrites() throws Exception {
        long before = jdbc.queryForObject("SELECT count(*) FROM recommendation_runs", Long.class);
        String game = "{\"contractVersion\":\"assistant-v1\",\"topic\":\"GAME\",\"intent\":\"SELECT_GAME\",\"locale\":\"tr\",\"genre\":\"rpg\"}";
        JsonNode result = assistant(token, game, 200);
        assertThat(result.path("template").asString()).isEqualTo("GAME_SELECTION");
        assertThat(result).isEqualTo(assistant(token, game, 200));
        assertThat(result.toString()).doesNotContain(A.toString(), "Bearer", "fpsSampleCount");
        String usage = "{\"contractVersion\":\"assistant-v1\",\"topic\":\"LTZ_USAGE\",\"intent\":\"VIEW_HISTORY\",\"locale\":\"en\"}";
        assertThat(assistant(token, usage, 200).path("template").asString()).isEqualTo("HISTORY_GUIDE");
        assertThat(assistant(token, usage.replace("VIEW_HISTORY", "GIVE_FEEDBACK"), 200).path("template").asString()).isEqualTo("FEEDBACK_GUIDE");
        assertThat(REQUESTS).allMatch(path -> path.equals("/api/v1/user/me/consents") || path.equals("/internal/v1/service-tokens"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM recommendation_runs", Long.class)).isEqualTo(before);
    }

    @Test
    void assistantHardwareRequiresOwnerAndMissingInputIsExplicit() throws Exception {
        String minimal = "{\"contractVersion\":\"assistant-v1\",\"topic\":\"HARDWARE\",\"intent\":\"ASSESS_GAME\",\"locale\":\"tr\"}";
        assertThat(assistant(token, minimal, 200).path("missingFields").size()).isEqualTo(3);
        String complete = minimal.substring(0, minimal.length() - 1) + ",\"hardwareProfileId\":\"" + PROFILE
                + "\",\"gameId\":\"" + GAME + "\",\"platform\":\"windows\"}";
        assertThat(assistant(token, complete, 200).path("template").asString()).isEqualTo("HARDWARE_UNKNOWN");
        mode = "below-minimum";
        assertThat(assistant(token, complete, 200).path("template").asString()).isEqualTo("HARDWARE_DEFICIT");
        REQUESTS.clear();
        assistant(auth.token(B, "PLAYER"), complete, 404);
        assertThat(REQUESTS).noneMatch(path -> path.endsWith("/metadata") || path.contains("performance-reference"));
    }

    @Test
    void assistantRejectsIdentityInjectionInvalidIntentAndShellPayloads() throws Exception {
        String body = "{\"contractVersion\":\"assistant-v1\",\"topic\":\"GAME\",\"intent\":\"SELECT_GAME\",\"locale\":\"tr\"}";
        assistant("invalid", body, 401);
        assistant(auth.serviceToken(A), body, 401);
        for (String invalid : List.of(body.replace("assistant-v1", "assistant-v2"), body.replace("SELECT_GAME", "GIVE_FEEDBACK"),
                body.replace("\"tr\"", "\"xx\""), body.replace("\"GAME\"", "\"CHAT\""),
                body.substring(0, body.length() - 1) + ",\"userId\":\"" + B + "\"}",
                body.substring(0, body.length() - 1) + ",\"genre\":\"$(whoami)\"}",
                body.substring(0, body.length() - 1) + ",\"hardwareProfileId\":\"" + PROFILE + "\"}")) {
            assistant(token, invalid, 400);
        }
        assertThat(REQUESTS).isEmpty();
    }

    @Test
    void assistantStopsAtConsentAndHardwareSourceFailures() throws Exception {
        String body = "{\"contractVersion\":\"assistant-v1\",\"topic\":\"HARDWARE\",\"intent\":\"ASSESS_GAME\",\"locale\":\"en\","
                + "\"hardwareProfileId\":\"" + PROFILE + "\",\"gameId\":\"" + GAME + "\",\"platform\":\"windows\"}";
        mode = "no-consent";
        assertThat(assistant(token, body, 403).path("code").asString()).isEqualTo("CONSENT_REQUIRED");
        assertThat(REQUESTS).containsExactly("/api/v1/user/me/consents");
        mode = "hardware-unavailable";
        assistant(token, body, 503);
    }

    private JsonNode assistant(String bearer, String body, int expected) throws Exception {
        return perform("/api/v1/recommendation/assistant", bearer, body, expected);
    }

    private JsonNode assess(String bearer, String body, int expected) throws Exception {
        return perform(PATH, bearer, body, expected);
    }

    private JsonNode perform(String path, String bearer, String body, int expected) throws Exception {
        var response = mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body)
                .header("Authorization", "Bearer " + bearer)).andExpect(status().is(expected)).andReturn().getResponse();
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
        return JSON.readTree(response.getContentAsByteArray());
    }
    private static String request(String resolution, String preset) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("hardwareProfileId", PROFILE);
        body.put("gameId", GAME);
        body.put("platform", "windows");
        body.put("resolution", resolution);
        body.put("graphicsPreset", preset);
        return JSON.writeValueAsString(body);
    }

    private static void handle(HttpExchange exchange) throws java.io.IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            if (path.contains(".well-known")) { reply(exchange, 200, auth.jwks()); return; }
            REQUESTS.add(exchange.getRequestURI().toString());
            if (path.equals("/api/v1/user/me/consents")) {
                reply(exchange, 200, "{\"recommendation\":" + !mode.equals("no-consent") + "}");
            } else if (path.equals("/internal/v1/service-tokens")) {
                var body = JSON.readTree(exchange.getRequestBody().readAllBytes());
                UUID account = UUID.fromString(body.path("accountId").asString());
                boolean performance = body.path("audience").asString().equals("performance-service");
                String token = performance ? auth.performanceToken(account) : auth.libraryToken(account);
                reply(exchange, 200, JSON.writeValueAsString(Map.of("accessToken", token, "tokenType", "Bearer", "expiresIn", 60,
                        "scope", performance ? "reference:read" : "signals:read")));
            } else if (path.equals("/api/v1/hardware/profiles/batch")) {
                hardware(exchange);
            } else if (path.endsWith("/metadata")) {
                game(exchange);
            } else if (path.contains("performance-reference")) {
                performance(exchange);
            } else reply(exchange, 404, "{}");
        } catch (Exception exception) {
            if (exchange.getResponseCode() == -1) reply(exchange, 500, "{}");
        } finally { exchange.close(); }
    }

    private static void hardware(HttpExchange exchange) throws Exception {
        var body = JSON.readTree(exchange.getRequestBody().readAllBytes()).path("profiles");
        assertThat(body.size()).isEqualTo(1);
        assertThat(body.get(0).path("size").asInt()).isEqualTo(100);
        String bearer = exchange.getRequestHeaders().getFirst("Authorization");
        String subject = com.nimbusds.jwt.SignedJWT.parse(bearer.substring(7)).getJWTClaimsSet().getSubject();
        if (!subject.equals(A.toString()) || !body.get(0).path("profileId").asString().equals(PROFILE.toString())) {
            reply(exchange, 404, "{}"); return;
        }
        if (mode.equals("hardware-unavailable")) { reply(exchange, 503, "{}"); return; }
        List<Object> components = new ArrayList<>(List.of(
                component("CPU", "CPU MODEL", "{\"socket\":\"AM4\"}"), component("GPU", "GPU MODEL", "{\"capacityGb\":8}"),
                component("RAM", "manual", "{\"capacityGb\":" + (mode.equals("below-minimum") ? 4 : 16) + ",\"memoryType\":\"DDR4\"}"),
                component("STORAGE", "manual", "{\"capacityGb\":" + (mode.equals("below-minimum") ? 1 : 512) + "}"),
                component("MOTHERBOARD", "manual", "{\"memoryType\":\"DDR4\",\"socket\":\""
                        + (mode.equals("incompatible-socket") ? "LGA1700" : "AM4") + "\"}")));
        if (mode.equals("empty-profile")) components.clear();
        if (mode.equals("duplicate-component")) components.add(components.getFirst());
        var page = Map.of("number", 0, "size", 100, "totalElements", components.size(), "hasNext", mode.equals("paged-profile"));
        reply(exchange, 200, JSON.writeValueAsString(Map.of("profiles", List.of(Map.of("profileId", mode.equals("wrong-profile") ? B : PROFILE,
                "profileVersion", 7, "components", components, "page", page)))));
    }
    private static Map<String, Object> component(String category, String model, String specs) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", UUID.nameUUIDFromBytes(category.getBytes(StandardCharsets.UTF_8)));
        result.put("category", category); result.put("modelName", model); result.put("quantity", 1);
        result.put("brandName", null); result.put("brandId", null); result.put("source", "MANUAL");
        result.put("specifications", JSON.readTree(mode.equals("invalid-capacity") ? "{\"capacityGb\":-1}" : specs));
        return result;
    }

    private static void game(HttpExchange exchange) throws Exception {
        if (mode.equals("game-timeout")) { Thread.sleep(500); reply(exchange, 200, "{}"); return; }
        if (mode.equals("game-redirect")) { exchange.getResponseHeaders().set("Location", "/redirect-target"); reply(exchange, 302, "{}"); return; }
        if (mode.equals("game-malformed")) { reply(exchange, 200, "[]"); return; }
        List<Object> requirements = mode.equals("missing-requirements") ? List.of() : List.of(
                Map.of("level", "MINIMUM", "platform", "windows", "cpu", "CPU MODEL", "gpu", "GPU MODEL", "ramMb", 8192, "storageMb", 10000, "os", "Windows"),
                Map.of("level", "RECOMMENDED", "platform", "windows", "cpu", "CPU MODEL", "gpu", "GPU MODEL", "ramMb", 32768, "storageMb", 20000, "os", "Windows"));
        Map<String, Object> body = new HashMap<>();
        body.put("gameId", mode.equals("wrong-game") ? B : GAME);
        body.put("adult", mode.equals("unknown-adult") ? null : mode.equals("adult"));
        body.put("delisted", false); body.put("systemRequirements", requirements);
        reply(exchange, 200, JSON.writeValueAsString(body));
    }

    private static void performance(HttpExchange exchange) throws Exception {
        if (mode.equals("performance-unavailable")) { reply(exchange, 503, "{}"); return; }
        var token = com.nimbusds.jwt.SignedJWT.parse(exchange.getRequestHeaders().getFirst("Authorization").substring(7)).getJWTClaimsSet();
        assertThat(token.getSubject()).isEqualTo("svc:recommendation-service");
        assertThat(token.getAudience()).containsExactly("performance-service");
        assertThat(token.getStringClaim("account_id")).isEqualTo(A.toString());
        assertThat(token.getStringClaim("scope")).isEqualTo("reference:read");
        assertThat(exchange.getRequestURI().getQuery()).isEqualTo("hardwareProfileId=" + PROFILE);
        long samples = mode.equals("no-fps") ? 0 : mode.equals("sparse-fps") ? 59 : 100;
        Map<String, Object> body = new HashMap<>();
        body.put("gameId", mode.equals("wrong-fps-game") ? B : GAME);
        body.put("hardwareProfileId", mode.equals("wrong-fps-profile") ? B : PROFILE);
        body.put("reliable", samples >= 60); body.put("sessionCount", samples == 0 ? 0 : 2);
        body.put("fpsSampleCount", mode.equals("negative-count") ? -1 : samples);
        body.putAll(PerformanceReferenceFixtureValues.numbers(mode, samples));
        body.put("lastMeasuredAt", samples == 0 ? null : "2026-11-14T12:00:00Z");
        reply(exchange, 200, JSON.writeValueAsString(body));
    }
    private static void reply(HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}

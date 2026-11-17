package com.ltz.hardware.infrastructure.client;

import com.ltz.hardware.infrastructure.security.HardwareAuthProperties;
import com.ltz.hardware.infrastructure.client.dto.request.HardwareServiceTokenRequest;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HardwareServiceClientsTest {
    private static final String SECRET = "synthetic-hardware-client-credential";
    private static final String TOKEN = "synthetic-service-token";
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final UUID viewer = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();
    private final UUID device = UUID.randomUUID();
    private final AtomicInteger authCalls = new AtomicInteger();
    private final AtomicInteger userCalls = new AtomicInteger();
    private final AtomicInteger performanceCalls = new AtomicInteger();
    private final CountDownLatch release = new CountDownLatch(1);
    private HttpServer server;
    private ExecutorService executor;
    private URI origin;
    private HardwareClientProperties properties;
    private HardwareServiceTokenClient tokens;
    private UserServiceHardwareVisibilityPolicy visibility;
    private PerformanceServiceHardwareSyncDevicePolicy devices;
    private PerformanceServiceSummaryReader summary;
    private String summaryBody;
    private int summaryStatus = 200;
    private UserServiceReviewAudiencePolicy reviewAudience;
    private final List<List<UUID>> authorBatches = new CopyOnWriteArrayList<>();
    private final Set<UUID> deniedAuthors = new HashSet<>();
    private boolean reviewReplies;
    private boolean incompleteSecondBatch;
    private volatile JsonNode tokenRequest;
    private volatile JsonNode privacyRequest;
    private volatile String targetAuthorization;
    private volatile String targetPath;
    private int authStatus = 200;
    private int userStatus = 200;
    private int deviceStatus = 204;
    private String authBody = "{\"accessToken\":\"" + TOKEN
            + "\",\"tokenType\":\"Bearer\",\"expiresIn\":60,\"scope\":\"privacy:read devices:verify summary:read\"}";
    private String userBody;
    private String contentType = "application/json";
    private String slowPath;

    @BeforeEach void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/internal/v1/service-tokens", exchange -> {
            authCalls.incrementAndGet();
            tokenRequest = mapper.readTree(exchange.getRequestBody().readAllBytes());
            respond(exchange, authStatus, authBody);
        });
        server.createContext("/internal/v1/privacy/decisions", exchange -> {
            userCalls.incrementAndGet();
            targetAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
            privacyRequest = mapper.readTree(exchange.getRequestBody().readAllBytes());
            if (reviewReplies) {
                var authors = new ArrayList<UUID>();
                privacyRequest.get("ownerIds").forEach(id -> authors.add(UUID.fromString(id.asString())));
                authorBatches.add(List.copyOf(authors));
                if (incompleteSecondBatch && authorBatches.size() == 2) authors.removeLast();
                var decisions = authors.stream().map(id -> Map.of("ownerId", id,
                        "setting", deniedAuthors.contains(id) ? "PRIVATE" : "PUBLIC",
                        "allowed", !deniedAuthors.contains(id), "friends", false)).toList();
                respond(exchange, userStatus, mapper.writeValueAsString(Map.of("decisions", decisions)));
            } else respond(exchange, userStatus, userBody);
        });
        server.createContext("/internal/v1/accounts/", exchange -> {
            performanceCalls.incrementAndGet();
            targetAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
            targetPath = exchange.getRequestURI().toString();
            if (exchange.getRequestURI().getPath().endsWith("/performance-summary")) respond(exchange, summaryStatus, summaryBody);
            else respond(exchange, deviceStatus, "");
        });
        server.start();
        origin = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        userBody = decision(owner, "PUBLIC", true, false);
        configure(SECRET);
    }

    private void configure(String secret) {
        properties = new HardwareClientProperties(origin, origin, secret);
        var auth = new HardwareAuthProperties(origin, "ltz-auth", "ltz-api", Duration.ofMillis(250), Duration.ofMillis(300));
        var http = new HardwareHttpClient(auth);
        tokens = new HardwareServiceTokenClient(properties, auth, http, mapper);
        visibility = new UserServiceHardwareVisibilityPolicy(properties, tokens, http, mapper);
        devices = new PerformanceServiceHardwareSyncDevicePolicy(properties, tokens, http);
        summary = new PerformanceServiceSummaryReader(properties, tokens, http, mapper);
        reviewAudience = new UserServiceReviewAudiencePolicy(properties, tokens, http, mapper);
    }

    private String summaryBody() {
        return """
            {"hardwareProfileId":"%s","sessionCount":1,"sampleCount":2,"lastMeasuredAt":"2026-11-12T00:00:01Z",
             "metrics":[{"metric":"FPS","unit":"FPS","count":2,"sessionCount":1,"avg":60,"min":50,"max":70,
               "firstAt":"2026-11-12T00:00:00Z","lastAt":"2026-11-12T00:00:01Z"}]}
            """.formatted(device);
    }

    @Test void summaryUsesBoundAuthPermissionAndDoesNotCacheTokensOrValues() {
        summaryBody = summaryBody();
        var result = summary.read(owner, device);
        assertThat(result.hardwareProfileId()).isEqualTo(device);
        assertThat(result.sampleCount()).isEqualTo(2);
        assertThat(result.metrics().getFirst().avg()).isEqualTo(60);
        assertThat(tokenRequest.get("accountId").asString()).isEqualTo(owner.toString());
        assertThat(tokenRequest.get("audience").asString()).isEqualTo("performance-service");
        assertThat(targetPath).isEqualTo("/internal/v1/accounts/" + owner + "/performance-summary?hardwareProfileId=" + device);
        assertThat(targetAuthorization).isEqualTo("Bearer " + TOKEN);
        summaryBody = "{\"hardwareProfileId\":\"" + device
                + "\",\"sessionCount\":3,\"sampleCount\":0,\"lastMeasuredAt\":null,\"metrics\":[]}";
        assertThat(summary.read(owner, device).metrics()).isEmpty();
        assertThat(authCalls).hasValue(2);
        assertThat(performanceCalls).hasValue(2);
    }

    @ParameterizedTest
    @ValueSource(ints = {204, 301, 302, 400, 401, 403, 404, 429, 500, 503})
    void summaryFailuresNeverBecomeEmptySuccess(int status) {
        summaryBody = summaryBody(); summaryStatus = status;
        assertThatThrownBy(() -> summary.read(owner, device))
                .isInstanceOf(com.ltz.hardware.domain.exception.HardwarePerformanceUnavailableException.class)
                .hasNoCause().hasMessageNotContaining(TOKEN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"profile", "sessions", "samples", "timestamp", "unit", "metric", "count", "range", "number", "duplicate", "missing", "type"})
    void summaryRejectsUntrustedShapeBindingAndMetricValues(String scenario) {
        var root = (tools.jackson.databind.node.ObjectNode) mapper.readTree(summaryBody());
        var metric = (tools.jackson.databind.node.ObjectNode) root.path("metrics").get(0);
        switch (scenario) {
            case "profile" -> root.put("hardwareProfileId", owner.toString());
            case "sessions" -> root.put("sessionCount", -1);
            case "samples" -> root.put("sampleCount", 3);
            case "timestamp" -> root.put("lastMeasuredAt", "2026-11-12T00:00:02Z");
            case "unit" -> metric.put("unit", "unknown");
            case "metric" -> metric.put("metric", "UNKNOWN");
            case "count" -> metric.put("count", 0);
            case "range" -> metric.put("min", 80);
            case "number" -> metric.put("avg", "60");
            case "duplicate" -> ((tools.jackson.databind.node.ArrayNode) root.get("metrics")).add(metric.deepCopy());
            case "missing" -> root.remove("metrics");
            default -> root.put("metrics", "not-an-array");
        }
        summaryBody = mapper.writeValueAsString(root);
        assertThatThrownBy(() -> summary.read(owner, device))
                .isInstanceOf(com.ltz.hardware.domain.exception.HardwarePerformanceUnavailableException.class);
    }

    @Test void summaryRejectsMissingScopeOversizeTimeoutAndCredentialWithoutLeakingBodies() {
        summaryBody = summaryBody();
        authBody = authBody.replace("summary:read", "");
        assertThatThrownBy(() -> summary.read(owner, device)).hasNoCause();
        assertThat(performanceCalls).hasValue(0);
        authBody = authBody.replace("devices:verify", "devices:verify summary:read");
        summaryBody = " ".repeat(65537);
        assertThatThrownBy(() -> summary.read(owner, device)).hasNoCause();
        summaryBody = summaryBody(); slowPath = "/internal/v1/accounts/" + owner + "/performance-summary";
        assertThatThrownBy(() -> summary.read(owner, device)).hasNoCause();
        configure(null);
        assertThatThrownBy(() -> summary.read(owner, device)).hasNoCause();
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        try (exchange) {
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Location", origin + "/must-not-follow");
            if (status == 204) { exchange.sendResponseHeaders(status, -1); return; }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            if (exchange.getRequestURI().getPath().equals(slowPath)) {
                exchange.getResponseBody().write(bytes, 0, 1);
                exchange.getResponseBody().flush();
                try { release.await(3, TimeUnit.SECONDS); }
                catch (InterruptedException _) { Thread.currentThread().interrupt(); }
                exchange.getResponseBody().write(bytes, 1, bytes.length - 1);
            } else exchange.getResponseBody().write(bytes);
        }
    }

    @AfterEach void stop() {
        release.countDown();
        server.stop(0);
        executor.close();
    }

    private static String decision(UUID ownerId, String setting, boolean allowed, boolean friends) {
        return "{\"decisions\":[{\"ownerId\":\"" + ownerId + "\",\"setting\":\"" + setting
                + "\",\"allowed\":" + allowed + ",\"friends\":" + friends + "}]}";
    }

    @Test void privacyUsesVerifiedViewerAndHardwareFieldWithItsOwnServiceCredential() {
        userBody = decision(owner, "FRIENDS", true, true);
        var result = visibility.evaluate(viewer, owner);
        assertThat(result.allowed()).isTrue();
        assertThat(result.friends()).isTrue();
        assertThat(tokenRequest.get("clientId").asString()).isEqualTo("hardware-service");
        assertThat(tokenRequest.get("clientSecret").asString()).isEqualTo(SECRET);
        assertThat(tokenRequest.get("audience").asString()).isEqualTo("user-service");
        assertThat(tokenRequest.has("accountId")).isFalse();
        assertThat(privacyRequest.get("viewerId").asString()).isEqualTo(viewer.toString());
        assertThat(privacyRequest.get("field").asString()).isEqualTo("HARDWARE");
        assertThat(privacyRequest.get("ownerIds").size()).isEqualTo(1);
        assertThat(privacyRequest.get("ownerIds").get(0).asString()).isEqualTo(owner.toString());
        assertThat(targetAuthorization).isEqualTo("Bearer " + TOKEN);
    }

    @Test void decisionsAreNotCachedAndPrivateOrUntrustedProjectionDeniesSharing() {
        assertThat(visibility.evaluate(viewer, owner).allowed()).isTrue();
        userBody = decision(owner, "PRIVATE", false, false);
        assertThat(visibility.evaluate(viewer, owner).allowed()).isFalse();
        userBody = decision(owner, "PUBLIC", false, false);
        assertThat(visibility.evaluate(viewer, owner).allowed()).isFalse();
        assertThat(authCalls.get()).isEqualTo(3);
    }

    @Test void deviceChecksBindEveryFreshTokenToTheAccountAndNeverCacheActiveState() {
        assertThat(devices.isActiveOwnedDevice(owner, device)).isTrue();
        assertThat(tokenRequest.get("audience").asString()).isEqualTo("performance-service");
        assertThat(tokenRequest.get("accountId").asString()).isEqualTo(owner.toString());
        assertThat(targetPath).isEqualTo("/internal/v1/accounts/" + owner + "/devices/" + device);
        assertThat(targetAuthorization).isEqualTo("Bearer " + TOKEN);
        deviceStatus = 404;
        assertThat(devices.isActiveOwnedDevice(owner, device)).isFalse();
        assertThat(authCalls.get()).isEqualTo(2);
    }

    @ParameterizedTest @ValueSource(ints = {200, 202, 301, 302, 401, 403, 404, 429, 500, 503})
    void onlyPerformance204AllowsSync(int status) {
        deviceStatus = status;
        assertThat(devices.isActiveOwnedDevice(owner, device)).isFalse();
        assertThat(performanceCalls.get()).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(ints = {204, 301, 302, 401, 403, 404, 429, 500, 503})
    void failedAuthNeverCallsUserOrPerformance(int status) {
        authStatus = status;
        assertThat(visibility.evaluate(viewer, owner).allowed()).isFalse();
        assertThat(devices.isActiveOwnedDevice(owner, device)).isFalse();
        assertThat(userCalls.get() + performanceCalls.get()).isZero();
    }

    @ParameterizedTest @ValueSource(strings = {"null", "{}", "[]", "not-json",
            "{\"decisions\":[]}", "{\"decisions\":[null]}", "{\"decisions\":[{},{}]}",
            "{\"decisions\":[{}]}"})
    void malformedPrivacyRepliesDeny(String body) {
        userBody = body;
        assertThat(visibility.evaluate(viewer, owner).allowed()).isFalse();
    }

    @ParameterizedTest @ValueSource(strings = {"owner", "setting", "allowed", "friends", "missingAllowed", "missingFriends"})
    void privacyReplyMustHaveExactOwnerAndTypedDecisions(String scenario) {
        userBody = switch (scenario) {
            case "owner" -> decision(UUID.randomUUID(), "PUBLIC", true, false);
            case "setting" -> decision(owner, "UNKNOWN", true, true);
            case "allowed" -> userBody.replace("\"allowed\":true", "\"allowed\":\"true\"");
            case "friends" -> userBody.replace("\"friends\":false", "\"friends\":1");
            case "missingAllowed" -> userBody.replace("\"allowed\":true,", "");
            default -> userBody.replace(",\"friends\":false", "");
        };
        assertThat(visibility.evaluate(viewer, owner).allowed()).isFalse();
    }

    @ParameterizedTest @ValueSource(ints = {204, 301, 302, 401, 403, 404, 429, 500, 503})
    void unavailableUserDeniesAndDoesNotFollowRedirects(int status) {
        userStatus = status;
        assertThat(visibility.evaluate(viewer, owner).allowed()).isFalse();
        assertThat(userCalls.get()).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(strings = {"null", "{}", "not-json", "tokenType", "accessToken", "expiresIn",
            "scope", "longLifetime", "shortLifetime", "stringLifetime", "whitespace"})
    void malformedAuthRepliesNeverGrantAccess(String scenario) {
        authBody = switch (scenario) {
            case "tokenType" -> authBody.replace("Bearer", "Basic");
            case "accessToken" -> authBody.replace(TOKEN, "");
            case "expiresIn" -> authBody.replace("\"expiresIn\":60,", "");
            case "scope" -> authBody.replace("privacy:read devices:verify", "other:read");
            case "longLifetime" -> authBody.replace(":60", ":61");
            case "shortLifetime" -> authBody.replace(":60", ":0");
            case "stringLifetime" -> authBody.replace(":60", ":\"60\"");
            case "whitespace" -> authBody.replace(TOKEN, "invalid token");
            default -> scenario;
        };
        assertThat(visibility.evaluate(viewer, owner).allowed()).isFalse();
        assertThat(devices.isActiveOwnedDevice(owner, device)).isFalse();
        assertThat(userCalls.get() + performanceCalls.get()).isZero();
    }

    @Test void missingCredentialAndMissingIdentitiesDenyWithoutMakingRequests() {
        configure("");
        assertThat(visibility.evaluate(viewer, owner).allowed()).isFalse();
        assertThat(devices.isActiveOwnedDevice(owner, device)).isFalse();
        assertThat(visibility.evaluate(null, owner).allowed()).isFalse();
        assertThat(visibility.evaluate(viewer, null).allowed()).isFalse();
        assertThat(devices.isActiveOwnedDevice(null, device)).isFalse();
        assertThat(devices.isActiveOwnedDevice(owner, null)).isFalse();
        assertThat(authCalls.get()).isZero();
        assertThatThrownBy(() -> tokens.deviceVerificationToken(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void oversizedRepliesAndWrongMediaTypesDeny() {
        authBody = "x".repeat(65537);
        assertThat(devices.isActiveOwnedDevice(owner, device)).isFalse();
        configure(SECRET);
        contentType = "text/html";
        assertThat(visibility.evaluate(viewer, owner).allowed()).isFalse();
    }

    @ParameterizedTest @ValueSource(strings = {"/internal/v1/service-tokens", "/internal/v1/privacy/decisions"})
    void stalledResponseBodiesAreBoundedByReadTimeout(String path) {
        slowPath = path;
        try (var caller = Executors.newVirtualThreadPerTaskExecutor()) {
            var result = caller.submit(() -> visibility.evaluate(viewer, owner));
            try { assertThat(result.get(2, TimeUnit.SECONDS).allowed()).isFalse(); }
            catch (Exception exception) { throw new AssertionError("HTTP read must time out", exception); }
            finally { release.countDown(); }
        }
    }

    @Test void refusedConnectionsDenyWithoutExposingTransportFailures() {
        server.stop(0);
        assertThat(visibility.evaluate(viewer, owner).allowed()).isFalse();
        assertThat(devices.isActiveOwnedDevice(owner, device)).isFalse();
    }

    @ParameterizedTest @ValueSource(strings = {"ftp://user-service", "http:invalid", "https://u:p@user-service",
            "https://user-service/path", "https://user-service?x=1", "https://user-service#fragment"})
    void configuredTargetsMustBeOrigins(String value) {
        URI invalid = URI.create(value);
        assertThatThrownBy(() -> new HardwareClientProperties(invalid, origin, SECRET)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HardwareClientProperties(origin, invalid, SECRET)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void credentialsAreRedactedAndConfigurationBoundsAreEnforced() {
        assertThat(properties.toString()).doesNotContain(SECRET);
        assertThat(new HardwareServiceTokenRequest("hardware-service", SECRET, "user-service", null).toString())
                .doesNotContain(SECRET);
        assertThat(new HardwareClientProperties(origin, origin, null).configured()).isFalse();
        assertThatThrownBy(() -> new HardwareClientProperties(null, origin, SECRET)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HardwareClientProperties(origin, null, SECRET)).isInstanceOf(IllegalArgumentException.class);
        String oversized = "x".repeat(257);
        assertThatThrownBy(() -> new HardwareClientProperties(origin, origin, oversized))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(List.of(properties.userServiceUrl(), properties.performanceServiceUrl())).containsOnly(origin);
    }

    @Test void debugLoggingDoesNotExposeServiceCredentialOrReturnedToken() throws IOException {
        var config = new Properties();
        try (var resource = getClass().getResourceAsStream("/application.properties")) { config.load(resource); }
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        Logger client = (Logger) LoggerFactory.getLogger("org.springframework.web.client");
        Logger converter = (Logger) LoggerFactory.getLogger("org.springframework.http.converter");
        var originalRoot = root.getLevel();
        var originalClient = client.getLevel();
        var originalConverter = converter.getLevel();
        var captured = new ListAppender<ILoggingEvent>();
        captured.start();
        root.addAppender(captured);
        try {
            root.setLevel(Level.TRACE);
            client.setLevel(Level.valueOf(config.getProperty("logging.level.org.springframework.web.client")));
            converter.setLevel(Level.valueOf(config.getProperty("logging.level.org.springframework.http.converter")));
            assertThat(visibility.evaluate(viewer, owner).allowed()).isTrue();
            assertThat(captured.list).allSatisfy(event ->
                    assertThat(event.getFormattedMessage()).doesNotContain(SECRET, TOKEN));
        } finally {
            root.detachAppender(captured);
            captured.stop();
            root.setLevel(originalRoot);
            client.setLevel(originalClient);
            converter.setLevel(originalConverter);
        }
    }

    @Test void reviewAuthorsAreBatchedBeforePaginationAndUseAnIndependentField() {
        reviewReplies = true;
        var authors = new HashSet<UUID>();
        for (int index = 0; index < 205; index++) authors.add(UUID.randomUUID());
        deniedAuthors.addAll(authors.stream().limit(104).toList());
        var result = reviewAudience.evaluate(viewer, authors);
        assertThat(result.allowOthers()).isTrue();
        assertThat(result.blockedAuthors()).isEqualTo(deniedAuthors);
        assertThat(authorBatches).extracting(List::size).containsExactly(100, 100, 5);
        assertThat(authorBatches.stream().flatMap(List::stream).collect(java.util.stream.Collectors.toSet())).isEqualTo(authors);
        assertThat(privacyRequest.get("field").asString()).isEqualTo("REVIEW_AUTHOR");
        assertThat(privacyRequest.get("viewerId").asString()).isEqualTo(viewer.toString());
        assertThat(authCalls.get()).isEqualTo(3);
    }

    @Test void anIncompleteLaterBatchDiscardsAllEarlierPermissions() {
        reviewReplies = true;
        incompleteSecondBatch = true;
        var authors = new HashSet<UUID>();
        for (int index = 0; index < 101; index++) authors.add(UUID.randomUUID());
        assertThat(reviewAudience.evaluate(viewer, authors).allowOthers()).isFalse();
        assertThat(authorBatches).hasSize(2);
    }

    @ParameterizedTest @ValueSource(strings = {"null", "{}", "[]", "not-json", "missing", "duplicate", "foreign",
            "allowed", "friends", "owner", "setting", "privateAllow", "noSetting", "noOwner"})
    void malformedOrIncompleteReviewDecisionsDenyEverything(String scenario) {
        userBody = switch (scenario) {
            case "missing" -> "{\"decisions\":[]}";
            case "duplicate" -> "{\"decisions\":[{},{}]}";
            case "foreign" -> decision(UUID.randomUUID(), "PUBLIC", true, false);
            case "allowed" -> userBody.replace("\"allowed\":true", "\"allowed\":\"true\"");
            case "friends" -> userBody.replace("\"friends\":false", "\"friends\":1");
            case "owner" -> userBody.replace(owner.toString(), "invalid-uuid");
            case "setting" -> decision(owner, "FRIENDS", true, true);
            case "privateAllow" -> decision(owner, "PRIVATE", true, false);
            case "noSetting" -> userBody.replace("\"setting\":\"PUBLIC\",", "");
            case "noOwner" -> userBody.replace("\"ownerId\":\"" + owner + "\",", "");
            default -> scenario;
        };
        assertThat(reviewAudience.evaluate(viewer, Set.of(owner)).allowOthers()).isFalse();
    }

    @ParameterizedTest @ValueSource(ints = {204, 301, 302, 401, 403, 404, 429, 500, 503})
    void reviewDecisionHttpFailuresKeepOnlyOwnerAccess(int status) {
        userStatus = status;
        var result = reviewAudience.evaluate(viewer, Set.of(owner));
        assertThat(result.allowOthers()).isFalse();
        assertThat(result.permits(viewer, owner)).isFalse();
        assertThat(result.permits(viewer, viewer)).isTrue();
    }

    @Test void authorLimitsNullAndEmptyCandidatesDenyWithoutPartialRequests() {
        var authors = new HashSet<UUID>();
        for (int index = 0; index < 1001; index++) authors.add(UUID.randomUUID());
        assertThat(reviewAudience.evaluate(viewer, authors).allowOthers()).isFalse();
        assertThat(reviewAudience.evaluate(viewer, Set.of()).allowOthers()).isFalse();
        assertThat(reviewAudience.evaluate(null, Set.of(owner)).allowOthers()).isFalse();
        assertThat(reviewAudience.evaluate(viewer, null).allowOthers()).isFalse();
        authors.clear();
        authors.add(null);
        assertThat(reviewAudience.evaluate(viewer, authors).allowOthers()).isFalse();
        assertThat(authCalls.get()).isZero();
    }

    @Test void aWellFormedSingleReviewDecisionAndPrivateDenyAreAccepted() {
        assertThat(reviewAudience.evaluate(viewer, Set.of(owner)).permits(viewer, owner)).isTrue();
        userBody = decision(owner, "PRIVATE", false, false);
        assertThat(reviewAudience.evaluate(viewer, Set.of(owner)).permits(viewer, owner)).isFalse();
    }

    @Test void reviewTransportTimeoutAndMissingCredentialFailClosed() {
        slowPath = "/internal/v1/privacy/decisions";
        assertThat(reviewAudience.evaluate(viewer, Set.of(owner)).allowOthers()).isFalse();
        release.countDown();
        configure("");
        assertThat(reviewAudience.evaluate(viewer, Set.of(owner)).allowOthers()).isFalse();
    }

    @Test void duplicateAndNonCanonicalOwnersCannotSubstituteForMissingDecisions() {
        String item = decision(owner, "PUBLIC", true, false);
        userBody = item.replace("]}", "," + item.substring("{\"decisions\":[".length()));
        assertThat(reviewAudience.evaluate(viewer, Set.of(owner, device)).allowOthers()).isFalse();
        userBody = decision(owner, "PUBLIC", true, false).replace(owner.toString(), "1-1-1-1-1");
        assertThat(reviewAudience.evaluate(viewer, Set.of(owner)).allowOthers()).isFalse();
    }
}

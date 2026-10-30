package com.ltz.library.api.controller;

import com.ltz.library.application.port.GameCatalogPort;
import com.ltz.library.domain.exception.CatalogUnavailableException;
import com.ltz.library.domain.exception.GameUnavailableException;
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
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Gerçek PostgreSQL/Flyway/JPA ve gerçek RSA/JWKS; Game HTTP adaptörü ayrı testte sınanır. */
@SpringBootTest(properties = "logging.level.root=WARN")
class LibraryApiTest {
    private static final String TEST_DATABASE = "library-phase1-test";
    private static final String TEST_USER = "ltz_library_phase1_test";
    /** CI kendi izole PostgreSQL'ini verir; yerelde (pre-push dahil) geliştirici DB'si yerine geçici konteyner açılır. */
    private static final PostgreSQLContainer POSTGRES = Boolean.getBoolean("ltz.test.external-postgres") ? null : startContainer();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID GAME = UUID.randomUUID();
    private static final KeyPair KEYS = keys();
    private static final HttpServer JWKS = jwks();
    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired private com.ltz.library.application.service.LibraryEntryWriter writer;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    private com.ltz.library.infrastructure.persistence.repository.LibraryEntryRepository entries;
    @MockitoBean private GameCatalogPort catalog;
    @MockitoBean private com.ltz.library.application.port.LibraryVisibilityPort visibility;
    private MockMvc mvc;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("ltz.security.jwt.jwk-set-uri", () -> "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/jwks");
        if (POSTGRES != null) {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
        }
    }

    @BeforeEach void setup() {
        requireTestDatabase(jdbc.queryForObject("SELECT current_database()", String.class),
                jdbc.queryForObject("SELECT current_user", String.class));
        jdbc.update("DELETE FROM library_entries");
        jdbc.update("DELETE FROM game_snapshots");
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @AfterAll static void stop() { JWKS.stop(0); }

    private static PostgreSQLContainer startContainer() {
        PostgreSQLContainer container = new PostgreSQLContainer("postgres:17-alpine")
                .withDatabaseName(TEST_DATABASE).withUsername(TEST_USER);
        container.start();
        return container;
    }

    private static void requireTestDatabase(String database, String user) {
        boolean dedicated = TEST_DATABASE.equals(database) && TEST_USER.equals(user);
        boolean ci = Boolean.getBoolean("ltz.test.external-postgres") && "library-service".equals(database) && "ltz_test".equals(user);
        org.junit.jupiter.api.Assertions.assertTrue(dedicated || ci, "Veri temizliği yalnız ayrılmış Library test veritabanında çalışır.");
    }

    @Test void refusesCleanupAgainstDeveloperDatabase() {
        org.junit.jupiter.api.Assertions.assertThrows(AssertionError.class, () -> requireTestDatabase("library_db", "ltz_dev"));
        org.junit.jupiter.api.Assertions.assertThrows(AssertionError.class, () -> requireTestDatabase("library-phase1-test", "ltz_dev"));
    }

    @Test void anEarlierTransactionMustNotBackdateAnEntryCreatedByAnotherTransaction() {
        var transaction = new org.springframework.transaction.support.TransactionTemplate(transactions);
        transaction.executeWithoutResult(status -> {
            jdbc.queryForObject("SELECT CURRENT_TIMESTAMP", java.sql.Timestamp.class);
            try (var worker = java.util.concurrent.Executors.newSingleThreadExecutor()) {
                worker.submit(() -> writer.write(USER, GAME, com.ltz.library.domain.enums.LibraryAction.COLLECT))
                        .get(10, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception error) { throw new IllegalStateException(error); }
            var changed = writer.write(USER, GAME, com.ltz.library.domain.enums.LibraryAction.FAVORITE);
            org.junit.jupiter.api.Assertions.assertFalse(changed.updatedAt().isBefore(changed.addedAt()),
                    "Eşzamanlı yazmada updatedAt addedAt değerinden önce olamaz.");
        });
    }

    @Test void addsValidGameWithoutRequiringFlagsOrOwnershipProof() throws Exception {
        add(token(USER, List.of("PLAYER")), "{\"gameId\":\"" + GAME + "\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.gameId").value(GAME.toString()))
                .andExpect(jsonPath("$.inCollection").value(true)).andExpect(jsonPath("$.favorite").value(false))
                .andExpect(jsonPath("$.completed").value(false));
        assertEquals(USER, jdbc.queryForObject("SELECT user_id FROM library_entries", UUID.class));
    }

    @Test void invalidGamesAndOutagesLeaveNoRecord() throws Exception {
        doThrow(new GameUnavailableException()).when(catalog).requireAvailable(GAME);
        add(token(USER, List.of("PLAYER")), "{\"gameId\":\"" + GAME + "\"}").andExpect(status().isNotFound());
        assertEquals(0, count());
        doThrow(new CatalogUnavailableException()).when(catalog).requireAvailable(GAME);
        add(token(USER, List.of("PLAYER")), "{\"gameId\":\"" + GAME + "\"}").andExpect(status().isServiceUnavailable());
        assertEquals(0, count());
    }

    @Test void removalIsAtomicIdempotentAndIsolatedFromOtherUsersAndCatalog() throws Exception {
        UUID other = UUID.randomUUID();
        for (UUID user : List.of(USER, other)) {
            add(token(user, List.of("PLAYER")), "{\"gameId\":\"" + GAME + "\"}").andExpect(status().isOk());
        }
        jdbc.update("UPDATE library_entries SET favorite = true, completed = true WHERE user_id = ?", USER);
        org.mockito.Mockito.clearInvocations(catalog);
        for (int i = 0; i < 2; i++) {
            mvc.perform(delete("/api/v1/library/entries/" + GAME).header("Authorization", "Bearer " + token(USER, List.of("PLAYER"))))
                    .andExpect(status().isNoContent());
        }
        assertEquals(1, count());
        assertEquals(other, jdbc.queryForObject("SELECT user_id FROM library_entries", UUID.class));
        verifyNoInteractions(catalog);
        mvc.perform(delete("/api/v1/library/entries/" + GAME)).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/library/entries/not-a-uuid").header("Authorization", "Bearer " + token(USER, List.of("PLAYER"))))
                .andExpect(status().isBadRequest());
    }

    @Test void accountErasureDeletesOnlyBoundAccountAndIsIdempotent() throws Exception {
        UUID other = UUID.randomUUID();
        for (UUID user : List.of(USER, other))
            add(token(user, List.of("PLAYER")), "{\"gameId\":\"" + GAME + "\"}").andExpect(status().isOk());
        Instant now = Instant.now();
        String serviceToken = sign(new JWTClaimsSet.Builder().issuer("ltz-auth").audience("library-service").subject("svc:auth-service")
                .issueTime(Date.from(now)).notBeforeTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(30)))
                .jwtID(UUID.randomUUID().toString()).claim("scope", "account:erase").claim("account_id", USER.toString()), "svc+jwt");
        for (int i = 0; i < 2; i++)
            mvc.perform(delete("/internal/v1/accounts/" + USER).header("Authorization", "Bearer " + serviceToken)).andExpect(status().isNoContent());
        assertEquals(1, count());
        assertEquals(other, jdbc.queryForObject("SELECT user_id FROM library_entries", UUID.class));
    }

    @Test void repeatedAdditionPreservesIdentityDatesAndExistingFlags() throws Exception {
        String bearer = token(USER, List.of("PLAYER"));
        add(bearer, "{\"gameId\":\"" + GAME + "\"}").andExpect(status().isOk());
        jdbc.update("UPDATE library_entries SET favorite = true, completed = true WHERE user_id = ?", USER);
        var before = jdbc.queryForMap("SELECT * FROM library_entries");
        add(bearer, "{\"gameId\":\"" + GAME + "\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.favorite").value(true)).andExpect(jsonPath("$.completed").value(true));
        assertEquals(before, jdbc.queryForMap("SELECT * FROM library_entries"));
    }

    @Test void concurrentAdditionProducesOneRowAndDatabaseRejectsDuplicateSql() throws Exception {
        String bearer = token(USER, List.of("PLAYER"));
        String body = "{\"gameId\":\"" + GAME + "\"}";
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var workers = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 16; i++) futures.add(workers.submit(() -> {
                start.await();
                add(bearer, body).andExpect(status().isOk());
                return null;
            }));
            start.countDown();
            for (var future : futures) future.get(20, java.util.concurrent.TimeUnit.SECONDS);
        }
        assertEquals(1, count());
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO library_entries (id, user_id, game_id) VALUES (?, ?, ?)", UUID.randomUUID(), USER, GAME));
    }

    @Test void rejectsInvalidTokenContractsBeforeCallingCatalog() throws Exception {
        Instant now = Instant.now();
        var invalid = List.of(
                claims().issuer("other"), claims().audience("library-service"), claims().audience((List<String>) null),
                claims().subject("1-1-1-1-1"), claims().subject(null),
                claims().issueTime(null), claims().notBeforeTime(null), claims().expirationTime(null),
                claims().expirationTime(Date.from(now.minusSeconds(60))).issueTime(Date.from(now.minusSeconds(120))),
                claims().notBeforeTime(Date.from(now.plusSeconds(120))),
                claims().issueTime(Date.from(now.plusSeconds(120))));
        for (var claim : invalid)
            add(sign(claim, "at+jwt"), "{\"gameId\":\"" + GAME + "\"}").andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401));
        add(sign(claims(), "svc+jwt"), "{\"gameId\":\"" + GAME + "\"}").andExpect(status().isUnauthorized());
        add(token(USER, List.of("PLAYER")) + "corrupt", "{\"gameId\":\"" + GAME + "\"}").andExpect(status().isUnauthorized());
        for (Object roles : List.of("PLAYER", List.of(123), List.of(), List.of("ROLE_PLAYER")))
            add(token(USER, roles), "{\"gameId\":\"" + GAME + "\"}").andExpect(status().isForbidden());
        verifyNoInteractions(catalog);
        assertEquals(0, count());
    }

    @Test void queryHeadersAndCookiesCannotSelectAnotherOwner() throws Exception {
        UUID other = UUID.randomUUID();
        mvc.perform(post("/api/v1/library/entries").queryParam("userId", other.toString())
                .header("X-User-Id", other.toString()).header("X-Role", "ADMIN")
                .header("Authorization", "Bearer " + token(USER, List.of("PLAYER")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"gameId\":\"" + GAME + "\"}"))
                .andExpect(status().isOk());
        assertEquals(USER, jdbc.queryForObject("SELECT user_id FROM library_entries", UUID.class));
        mvc.perform(post("/api/v1/library/entries").cookie(new jakarta.servlet.http.Cookie("ltz_access", token(USER, List.of("PLAYER"))))
                .header("X-User-Id", USER.toString()).header("X-Role", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON).content("{\"gameId\":\"" + GAME + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test void catalogValidationRunsOutsideLocalTransaction() throws Exception {
        org.mockito.Mockito.doAnswer(invocation -> {
            org.junit.jupiter.api.Assertions.assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            return null;
        }).when(catalog).requireAvailable(GAME);
        add(token(USER, List.of("PLAYER")), "{\"gameId\":\"" + GAME + "\"}").andExpect(status().isOk());
    }

    @Test void markingMissingGameCreatesOnlyRequestedFlagAndRepeatsWithoutChangingDates() throws Exception {
        String bearer = token(USER, List.of("PLAYER"));
        for (String flag : List.of("favorite", "completed")) {
            jdbc.update("DELETE FROM library_entries");
            mark(bearer, flag).andExpect(status().isOk()).andExpect(jsonPath("$.inCollection").value(true))
                    .andExpect(jsonPath("$.favorite").value(flag.equals("favorite")))
                    .andExpect(jsonPath("$.completed").value(flag.equals("completed")));
            var before = jdbc.queryForMap("SELECT * FROM library_entries");
            mark(bearer, flag).andExpect(status().isOk());
            assertEquals(before, jdbc.queryForMap("SELECT * FROM library_entries"));
        }
    }

    @Test void concurrentCollectFavoriteAndCompleteDoNotLoseFlagsOrCreateDuplicates() throws Exception {
        String bearer = token(USER, List.of("PLAYER"));
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var workers = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 24; i++) {
                int action = i % 3;
                futures.add(workers.submit(() -> {
                    start.await();
                    if (action == 0) add(bearer, "{\"gameId\":\"" + GAME + "\"}").andExpect(status().isOk());
                    else mark(bearer, action == 1 ? "favorite" : "completed").andExpect(status().isOk());
                    return null;
                }));
            }
            start.countDown();
            for (var future : futures) future.get(20, java.util.concurrent.TimeUnit.SECONDS);
        }
        assertEquals(1, count());
        var state = jdbc.queryForMap("SELECT favorite, completed FROM library_entries");
        assertEquals(true, state.get("favorite"));
        assertEquals(true, state.get("completed"));
    }

    @Test void invalidGamesAndOutagesNeverAutoCreateAndFlagsRequireIdentity() throws Exception {
        for (String flag : List.of("favorite", "completed")) {
            doThrow(new GameUnavailableException()).when(catalog).requireAvailable(GAME);
            mark(token(USER, List.of("PLAYER")), flag).andExpect(status().isNotFound());
            doThrow(new CatalogUnavailableException()).when(catalog).requireAvailable(GAME);
            mark(token(USER, List.of("PLAYER")), flag).andExpect(status().isServiceUnavailable());
            mvc.perform(put("/api/v1/library/entries/" + GAME + "/" + flag)).andExpect(status().isUnauthorized());
            mark(token(USER, List.of()), flag).andExpect(status().isForbidden());
        }
        assertEquals(0, count());
    }

    @Test void productionAuthIssuerAndGatewayCookieCsrfFiltersAreCompatible(@org.junit.jupiter.api.io.TempDir java.nio.file.Path output) throws Exception {
        try (var auth = new com.ltz.library.infrastructure.security.ProductionAuthSupport(output, KEYS)) {
            String bearer = auth.token(USER, "PLAYER");
            add(bearer, "{\"gameId\":\"" + GAME + "\"}").andExpect(status().isOk());
            var throughGateway = MockMvcBuilders.webAppContextSetup(context).addFilters(auth.gatewayFilters()).apply(springSecurity()).build();
            var cookies = new jakarta.servlet.http.Cookie[]{new jakarta.servlet.http.Cookie("ltz_access", bearer),
                    new jakarta.servlet.http.Cookie("XSRF-TOKEN", "library-test-csrf")};
            throughGateway.perform(put("/api/v1/library/entries/" + GAME + "/favorite").cookie(cookies))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
            throughGateway.perform(put("/api/v1/library/entries/" + GAME + "/favorite").cookie(cookies)
                    .header("X-XSRF-TOKEN", "library-test-csrf").header("Origin", "https://wrong.example.test"))
                    .andExpect(status().isForbidden());
            throughGateway.perform(put("/api/v1/library/entries/" + GAME + "/favorite").cookie(cookies)
                    .header("X-XSRF-TOKEN", "library-test-csrf").header("Origin", "https://app.example.test"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.favorite").value(true));
            for (String flag : List.of("favorite", "completed")) {
                throughGateway.perform(put("/api/v1/library/entries/" + GAME + "/" + flag).cookie(cookies)
                        .header("X-XSRF-TOKEN", "library-test-csrf").header("Origin", "https://app.example.test"))
                        .andExpect(status().isOk());
                String list = flag.equals("favorite") ? "favorites" : "completed";
                throughGateway.perform(get("/api/v1/library/entries/" + list).cookie(cookies))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
                throughGateway.perform(delete("/api/v1/library/entries/" + GAME + "/" + flag).cookie(cookies))
                        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
                throughGateway.perform(delete("/api/v1/library/entries/" + GAME + "/" + flag).cookie(cookies)
                        .header("X-XSRF-TOKEN", "library-test-csrf").header("Origin", "https://app.example.test"))
                        .andExpect(status().isNoContent());
                throughGateway.perform(get("/api/v1/library/entries/" + list).cookie(cookies))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
            }
            throughGateway.perform(delete("/internal/v1/accounts/" + USER).header("Authorization", "Bearer " + auth.serviceToken(USER)))
                    .andExpect(status().isNoContent());
            assertEquals(0, count());
        }
    }

    private org.springframework.test.web.servlet.ResultActions mark(String bearer, String flag) throws Exception {
        return mvc.perform(put("/api/v1/library/entries/" + GAME + "/" + flag).header("Authorization", "Bearer " + bearer));
    }

    @Test void rejectsMissingIdentityUnknownRolesAndClientSuppliedUserId() throws Exception {
        mvc.perform(post("/api/v1/library/entries").contentType(MediaType.APPLICATION_JSON).content("{\"gameId\":\"" + GAME + "\"}"))
                .andExpect(status().isUnauthorized());
        add(token(USER, List.of("UNKNOWN")), "{\"gameId\":\"" + GAME + "\"}").andExpect(status().isForbidden());
        add(token(USER, List.of("PLAYER")), "{\"gameId\":\"" + GAME + "\",\"userId\":\"" + UUID.randomUUID() + "\"}")
                .andExpect(status().isBadRequest());
        for (String body : List.of("{}", "{\"gameId\":null}", "{\"gameId\":\"invalid\"}", "{"))
            add(token(USER, List.of("PLAYER")), body).andExpect(status().isBadRequest());
        verifyNoInteractions(catalog);
        assertEquals(0, count());
    }

    @Test void unfavoritePreservesCollectionCompletedAndRepeatedRequestIsNoOp() throws Exception {
        String bearer = token(USER, List.of("PLAYER"));
        mark(bearer, "favorite").andExpect(status().isOk());
        mark(bearer, "completed").andExpect(status().isOk());
        var before = jdbc.queryForMap("SELECT * FROM library_entries");
        org.mockito.Mockito.clearInvocations(catalog);
        clear(bearer, "favorite").andExpect(status().isNoContent());
        var after = jdbc.queryForMap("SELECT * FROM library_entries");
        assertEquals(before.get("id"), after.get("id"));
        assertEquals(before.get("added_at"), after.get("added_at"));
        assertEquals(true, after.get("in_collection"));
        assertEquals(false, after.get("favorite"));
        assertEquals(true, after.get("completed"));
        assertEquals(((Number) before.get("version")).longValue() + 1, ((Number) after.get("version")).longValue());
        clear(bearer, "favorite").andExpect(status().isNoContent());
        assertEquals(after, jdbc.queryForMap("SELECT * FROM library_entries"));
        verifyNoInteractions(catalog);
    }

    @Test void missingUnfavoriteDoesNotCreateAndFavoritesCanBeReappliedToSameEntry() throws Exception {
        String bearer = token(USER, List.of("PLAYER"));
        clear(bearer, "favorite").andExpect(status().isNoContent());
        assertEquals(0, count());
        verifyNoInteractions(catalog);
        mark(bearer, "favorite").andExpect(status().isOk());
        UUID id = jdbc.queryForObject("SELECT id FROM library_entries", UUID.class);
        clear(bearer, "favorite").andExpect(status().isNoContent());
        mark(bearer, "favorite").andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id.toString()));
        assertEquals(1, count());
    }

    @Test void favoriteListContainsOnlyOwnerFavoritesWithStableBoundedPages() throws Exception {
        UUID other = UUID.randomUUID();
        String first = "00000000-0000-4000-8000-000000000001";
        String second = "00000000-0000-4000-8000-000000000002";
        fixture(first, USER, true, false);
        fixture(second, USER, true, true);
        fixture("00000000-0000-4000-8000-000000000003", USER, false, true);
        fixture("00000000-0000-4000-8000-000000000004", other, true, false);
        String bearer = token(USER, List.of("PLAYER"));
        listed(bearer, "favorites", "").andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2)).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.size").value(20)).andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.items[0].id").value(second));
        listed(bearer, "favorites", "?size=1&page=0&userId=" + other).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].id").value(second));
        listed(bearer, "favorites", "?size=1&page=1").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(first)).andExpect(jsonPath("$.totalElements").value(2));
        listed(bearer, "favorites", "?size=1&page=2").andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.totalElements").value(2));
        verifyNoInteractions(catalog);
    }

    @Test void favoriteRemovalCannotAffectAnotherUserAndEmptyListHasStableShape() throws Exception {
        String other = "00000000-0000-4000-8000-000000000005";
        fixture(other, UUID.randomUUID(), true, true);
        String bearer = token(USER, List.of("PLAYER"));
        mvc.perform(delete("/api/v1/library/entries/" + jdbc.queryForObject("SELECT game_id FROM library_entries", UUID.class) + "/favorite")
                .queryParam("userId", UUID.randomUUID().toString()).header("X-User-Id", UUID.randomUUID().toString())
                .header("Authorization", "Bearer " + bearer)).andExpect(status().isNoContent());
        assertEquals(true, jdbc.queryForObject("SELECT favorite FROM library_entries", Boolean.class));
        listed(bearer, "favorites", "").andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.totalElements").value(0));
        verifyNoInteractions(catalog);
    }

    @Test void favoriteListRejectsInvalidPagingAndAcceptsMaximumSizeAndLargeOffset() throws Exception {
        String bearer = token(USER, List.of("PLAYER"));
        for (String query : List.of("?page=-1", "?size=0", "?size=51", "?size=abc", "?page=2147483648"))
            listed(bearer, "favorites", query).andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        listed(bearer, "favorites", "?page=2147483647&size=50").andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.totalElements").value(0));
        verifyNoInteractions(catalog);
    }

    @Test void favoriteReadAndRemovalRequireUserJwtAndRole() throws Exception {
        mvc.perform(get("/api/v1/library/entries/favorites")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/library/entries/" + GAME + "/favorite")).andExpect(status().isUnauthorized());
        listed(token(USER, List.of()), "favorites", "").andExpect(status().isForbidden());
        clear(token(USER, List.of()), "favorite").andExpect(status().isForbidden());
        listed(sign(claims(), "svc+jwt"), "favorites", "").andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/library/entries/invalid/favorite")
                .header("Authorization", "Bearer " + token(USER, List.of("PLAYER")))).andExpect(status().isBadRequest());
        verifyNoInteractions(catalog);
    }

    @Test void concurrentFavoriteEnableAndDisablePreserveCompletedAndSingleCollectionRow() throws Exception {
        String bearer = token(USER, List.of("PLAYER"));
        mark(bearer, "completed").andExpect(status().isOk());
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var workers = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 16; i++) {
                boolean enable = i % 2 == 0;
                futures.add(workers.submit(() -> {
                    start.await();
                    if (enable) mark(bearer, "favorite").andExpect(status().isOk());
                    else clear(bearer, "favorite").andExpect(status().isNoContent());
                    return null;
                }));
            }
            start.countDown();
            for (var future : futures) future.get(20, java.util.concurrent.TimeUnit.SECONDS);
        }
        assertEquals(1, count());
        assertEquals(true, jdbc.queryForObject("SELECT completed AND in_collection FROM library_entries", Boolean.class));
    }

    @Test void uncompletePreservesFavoriteAndCollectionAndRepeatIsNoOp() throws Exception {
        String bearer = token(USER, List.of("PLAYER"));
        mark(bearer, "favorite").andExpect(status().isOk());
        mark(bearer, "completed").andExpect(status().isOk());
        var before = jdbc.queryForMap("SELECT * FROM library_entries");
        org.mockito.Mockito.clearInvocations(catalog);
        clear(bearer, "completed").andExpect(status().isNoContent());
        var after = jdbc.queryForMap("SELECT * FROM library_entries");
        assertEquals(before.get("id"), after.get("id"));
        assertEquals(before.get("added_at"), after.get("added_at"));
        assertEquals(true, after.get("favorite"));
        assertEquals(true, after.get("in_collection"));
        assertEquals(false, after.get("completed"));
        assertEquals(((Number) before.get("version")).longValue() + 1, ((Number) after.get("version")).longValue());
        clear(bearer, "completed").andExpect(status().isNoContent());
        assertEquals(after, jdbc.queryForMap("SELECT * FROM library_entries"));
        verifyNoInteractions(catalog);
        mark(bearer, "completed").andExpect(status().isOk()).andExpect(jsonPath("$.id").value(before.get("id").toString()));
        assertEquals(1, count());
    }

    @Test void missingUncompleteDoesNotCreateAndBothFlagsMayBeFalseInCollection() throws Exception {
        String bearer = token(USER, List.of("PLAYER"));
        clear(bearer, "completed").andExpect(status().isNoContent());
        assertEquals(0, count());
        verifyNoInteractions(catalog);
        mark(bearer, "completed").andExpect(status().isOk());
        org.mockito.Mockito.clearInvocations(catalog);
        clear(bearer, "completed").andExpect(status().isNoContent());
        assertEquals(1, count());
        assertEquals(true, jdbc.queryForObject("SELECT in_collection AND NOT favorite AND NOT completed FROM library_entries", Boolean.class));
        listed(bearer, "completed", "").andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
        verifyNoInteractions(catalog);
    }

    @Test void completedListIsIndependentOfFavoritesAndIsolatedWithStablePages() throws Exception {
        UUID other = UUID.randomUUID();
        String first = "00000000-0000-4000-8000-000000000001";
        String second = "00000000-0000-4000-8000-000000000002";
        fixture(first, USER, false, true);
        fixture(second, USER, true, true);
        fixture("00000000-0000-4000-8000-000000000003", USER, true, false);
        fixture("00000000-0000-4000-8000-000000000004", other, true, true);
        String bearer = token(USER, List.of("PLAYER"));
        listed(bearer, "completed", "").andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2)).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[0].id").value(second));
        listed(bearer, "completed", "?size=1&page=1&userId=" + other).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(first));
        listed(bearer, "completed", "?page=2&size=1").andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.totalElements").value(2));
        UUID otherGame = jdbc.queryForObject("SELECT game_id FROM library_entries WHERE user_id = ?", UUID.class, other);
        mvc.perform(delete("/api/v1/library/entries/" + otherGame + "/completed")
                .header("Authorization", "Bearer " + bearer)).andExpect(status().isNoContent());
        assertEquals(true, jdbc.queryForObject("SELECT completed FROM library_entries WHERE user_id = ?", Boolean.class, other));
        verifyNoInteractions(catalog);
    }

    @Test void completedReadAndRemovalValidateIdentityRoleAndInput() throws Exception {
        String bearer = token(USER, List.of("PLAYER"));
        mvc.perform(get("/api/v1/library/entries/completed")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/library/entries/" + GAME + "/completed")).andExpect(status().isUnauthorized());
        listed(token(USER, List.of()), "completed", "").andExpect(status().isForbidden());
        clear(token(USER, List.of()), "completed").andExpect(status().isForbidden());
        listed(sign(claims(), "svc+jwt"), "completed", "").andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/library/entries/invalid/completed").header("Authorization", "Bearer " + bearer))
                .andExpect(status().isBadRequest());
        for (String query : List.of("?page=-1", "?size=0", "?size=51", "?page=abc"))
            listed(bearer, "completed", query).andExpect(status().isBadRequest());
        listed(bearer, "completed", "?page=2147483647&size=50").andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
        verifyNoInteractions(catalog);
    }

    @Test void concurrentCompletedEnableAndDisableNeverClearFavoriteOrDuplicateCollection() throws Exception {
        String bearer = token(USER, List.of("PLAYER"));
        mark(bearer, "favorite").andExpect(status().isOk());
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var workers = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 16; i++) {
                boolean enable = i % 2 == 0;
                futures.add(workers.submit(() -> {
                    start.await();
                    if (enable) mark(bearer, "completed").andExpect(status().isOk());
                    else clear(bearer, "completed").andExpect(status().isNoContent());
                    return null;
                }));
            }
            start.countDown();
            for (var future : futures) future.get(20, java.util.concurrent.TimeUnit.SECONDS);
        }
        assertEquals(1, count());
        assertEquals(true, jdbc.queryForObject("SELECT favorite AND in_collection FROM library_entries", Boolean.class));
    }

    @Test void flagListUsesOneSnapshotForRowsAndTotalDuringConcurrentWrite() throws Exception {
        writer.write(USER, GAME, com.ltz.library.domain.enums.LibraryAction.FAVORITE);
        org.mockito.Mockito.doAnswer(invocation -> {
            Object snapshot = invocation.callRealMethod();
            try (var worker = java.util.concurrent.Executors.newSingleThreadExecutor()) {
                worker.submit(() -> writer.write(USER, UUID.randomUUID(), com.ltz.library.domain.enums.LibraryAction.FAVORITE))
                        .get(10, java.util.concurrent.TimeUnit.SECONDS);
            }
            return snapshot;
        }).when(entries).findFlagged(USER, com.ltz.library.domain.enums.LibraryFlag.FAVORITE, 0, 20);
        listed(token(USER, List.of("PLAYER")), "favorites", "").andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.totalElements").value(1));
        assertEquals(2, count());
        verifyNoInteractions(catalog);
    }

    @org.junit.jupiter.api.Nested
    class CollectionTests {

        @Test void collectionListsAllOwnerEntriesWithOneBatchAndPreservesMissingGame() throws Exception {
            fixture("00000000-0000-4000-8000-000000000001", USER, false, false);
            fixture("00000000-0000-4000-8000-000000000002", USER, true, true);
            fixture("00000000-0000-4000-8000-000000000003", UUID.randomUUID(), true, false);
            UUID known = jdbc.queryForObject("SELECT game_id FROM library_entries WHERE id='00000000-0000-4000-8000-000000000002'", UUID.class);
            when(catalog.summaries(org.mockito.ArgumentMatchers.anyList())).thenReturn(java.util.Map.of(known,
                    new com.ltz.library.application.port.GameSummary(known, "Example", "https://cdn.example.test/cover.jpg", "EXTERNAL", "INDEX_ONLY", "MAIN_GAME")));
            mvc.perform(get("/api/v1/library/entries").header("Authorization", "Bearer " + token(USER, List.of("PLAYER"))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.items.length()").value(2))
                    .andExpect(jsonPath("$.items[0].game.title").value("Example"))
                    .andExpect(jsonPath("$.items[0].entry.favorite").value(true))
                    .andExpect(jsonPath("$.items[0].entry.completed").value(true))
                    .andExpect(jsonPath("$.items[1].game.status").value("UNAVAILABLE"))
                    .andExpect(jsonPath("$.metadataPartial").value(true));
            verify(catalog).summaries(org.mockito.ArgumentMatchers.argThat(ids -> ids.size() == 2));
            assertEquals(3, count());
        }

        @Test void collectionSurvivesCatalogOutageAndCallsCatalogOutsideDatabaseTransaction() throws Exception {
            fixture("00000000-0000-4000-8000-000000000001", USER, true, true);
            when(catalog.summaries(org.mockito.ArgumentMatchers.anyList())).thenAnswer(invocation -> {
                org.junit.jupiter.api.Assertions.assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
                throw new CatalogUnavailableException();
            });
            mvc.perform(get("/api/v1/library/entries").header("Authorization", "Bearer " + token(USER, List.of("PLAYER"))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].game.status").value("TEMPORARILY_UNAVAILABLE"))
                    .andExpect(jsonPath("$.items[0].entry.favorite").value(true))
                    .andExpect(jsonPath("$.items[0].entry.completed").value(true))
                    .andExpect(jsonPath("$.totalElements").value(1));
            assertEquals(1, count());
        }

        @Test void emptyCollectionAndUnauthorizedReadDoNotCallGame() throws Exception {
            mvc.perform(get("/api/v1/library/entries")).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/v1/library/entries").header("Authorization", "Bearer " + token(USER, List.of())))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/library/entries").header("Authorization", "Bearer " + token(USER, List.of("PLAYER"))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty())
                    .andExpect(jsonPath("$.totalElements").value(0)).andExpect(jsonPath("$.metadataPartial").value(false));
            verifyNoInteractions(catalog);
        }

        @Test void collectionBooleanFiltersUseAndAndNeverIncludeAnotherOwner() throws Exception {
            fixture("00000000-0000-4000-8000-000000000001", USER, false, false);
            fixture("00000000-0000-4000-8000-000000000002", USER, true, false);
            fixture("00000000-0000-4000-8000-000000000003", USER, false, true);
            fixture("00000000-0000-4000-8000-000000000004", USER, true, true);
            UUID other = UUID.randomUUID();
            fixture("00000000-0000-4000-8000-000000000005", other, true, true);
            String bearer = token(USER, List.of("PLAYER"));
            for (var test : java.util.Map.of("", 4, "favorite=true", 2, "favorite=false", 2,
                    "completed=true", 2, "completed=false", 2, "favorite=true&completed=true", 1,
                    "favorite=true&completed=false", 1, "favorite=false&completed=true", 1,
                    "favorite=false&completed=false", 1).entrySet()) {
                mvc.perform(get("/api/v1/library/entries?" + test.getKey() + "&userId=" + other)
                        .header("X-User-Id", other.toString()).header("Authorization", "Bearer " + bearer))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(test.getValue()))
                        .andExpect(jsonPath("$.items.length()").value(test.getValue()));
            }
            mvc.perform(get("/api/v1/library/entries?favorite=invalid").header("Authorization", "Bearer " + bearer))
                    .andExpect(status().isBadRequest());
        }

        @Test void collectionSortsTitlesGloballyBeforePagingAndKeepsUnknownTitlesLast() throws Exception {
            for (int i = 1; i <= 4; i++) fixture("00000000-0000-4000-8000-00000000000" + i, USER, i != 4, false);
            var rows = jdbc.queryForList("SELECT id,game_id FROM library_entries ORDER BY id");
            var cards = new java.util.HashMap<UUID, com.ltz.library.application.port.GameSummary>();
            String[] titles = {"Zulu", "Alpha", "alpha", null};
            for (int i = 0; i < rows.size(); i++) {
                UUID id = (UUID) rows.get(i).get("game_id");
                cards.put(id, new com.ltz.library.application.port.GameSummary(id, titles[i], null, "INTERNAL", "INDEX_ONLY", "MAIN_GAME"));
            }
            when(catalog.summaries(org.mockito.ArgumentMatchers.anyList())).thenReturn(cards);
            String bearer = token(USER, List.of("PLAYER"));
            collectionGet(bearer, "?sort=TITLE&direction=ASC&size=1").andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].entry.id").value(rows.get(1).get("id").toString()))
                    .andExpect(jsonPath("$.metadataPartial").value(true));
            collectionGet(bearer, "?sort=TITLE&direction=ASC&size=1&page=1").andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].entry.id").value(rows.get(2).get("id").toString()));
            collectionGet(bearer, "?sort=TITLE&direction=DESC&size=1").andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].game.title").value("Zulu"));
            for (String direction : List.of("ASC", "DESC"))
                collectionGet(bearer, "?sort=TITLE&direction=" + direction + "&size=1&page=3").andExpect(status().isOk())
                        .andExpect(jsonPath("$.items[0].game.title").doesNotExist()).andExpect(jsonPath("$.totalElements").value(4));
            collectionGet(bearer, "?sort=TITLE&favorite=true").andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.metadataPartial").value(false));
            verify(catalog, times(5)).summaries(org.mockito.ArgumentMatchers.argThat(ids -> ids.size() == 4));
            verify(catalog).summaries(org.mockito.ArgumentMatchers.argThat(ids -> ids.size() == 3));
        }

        @Test void collectionSortsAddedTimeInBothDirectionsAndDoesNotGuessNamesDuringOutage() throws Exception {
            fixture("00000000-0000-4000-8000-000000000001", USER, true, false);
            fixture("00000000-0000-4000-8000-000000000002", USER, true, false);
            jdbc.update("UPDATE library_entries SET added_at='2026-10-29T12:00:00Z' WHERE id='00000000-0000-4000-8000-000000000002'");
            String bearer = token(USER, List.of("PLAYER"));
            collectionGet(bearer, "?sort=ADDED_AT&direction=ASC&size=1").andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].entry.id").value("00000000-0000-4000-8000-000000000002"));
            collectionGet(bearer, "?sort=ADDED_AT&direction=DESC&size=1").andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].entry.id").value("00000000-0000-4000-8000-000000000001"));
            when(catalog.summaries(org.mockito.ArgumentMatchers.anyList())).thenThrow(new CatalogUnavailableException());
            collectionGet(bearer, "?sort=TITLE&direction=DESC").andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].entry.id").value("00000000-0000-4000-8000-000000000001"))
                    .andExpect(jsonPath("$.metadataPartial").value(true)).andExpect(jsonPath("$.totalElements").value(2));
            assertEquals(2, count());
            collectionGet(bearer, "?sort=invalid").andExpect(status().isBadRequest());
            collectionGet(bearer, "?direction=invalid").andExpect(status().isBadRequest());
        }

        private org.springframework.test.web.servlet.ResultActions collectionGet(String bearer, String query) throws Exception {
            return mvc.perform(get("/api/v1/library/entries" + query).header("Authorization", "Bearer " + bearer));
        }

        @Test void collectionPagingValidatesBoundsAndAvoidsMetadataForOutOfRangePages() throws Exception {
            String bearer = token(USER, List.of("PLAYER"));
            for (String query : List.of("?page=-1", "?size=0", "?size=51", "?size=abc", "?page=2147483648"))
                collectionGet(bearer, query).andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
            verifyNoInteractions(catalog);
            fixture("00000000-0000-4000-8000-000000000001", USER, true, true);
            for (String sort : List.of("ADDED_AT", "TITLE"))
                collectionGet(bearer, "?sort=" + sort + "&page=2147483647&size=50").andExpect(status().isOk())
                        .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.totalElements").value(1))
                        .andExpect(jsonPath("$.page").value(2147483647)).andExpect(jsonPath("$.size").value(50));
            verifyNoInteractions(catalog);
            collectionGet(bearer, "").andExpect(status().isOk()).andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(20));
        }

        @Test void collectionPagesDoNotRepeatOrSkipFilteredRowsWithIdenticalDates() throws Exception {
            for (int i = 1; i <= 12; i++) fixture(String.format("00000000-0000-4000-8000-%012d", i), USER, i % 2 == 0, true);
            String bearer = token(USER, List.of("PLAYER"));
            for (String direction : List.of("ASC", "DESC")) {
                var seen = new java.util.HashSet<String>();
                for (int page = 0; page < 3; page++) {
                    var result = collectionGet(bearer, "?favorite=true&completed=true&direction=" + direction + "&size=2&page=" + page)
                            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(6))
                            .andExpect(jsonPath("$.items.length()").value(2)).andReturn();
                    List<String> ids = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.items[*].entry.id");
                    for (String id : ids) org.junit.jupiter.api.Assertions.assertTrue(seen.add(id));
                }
                assertEquals(6, seen.size());
            }
            listed(bearer, "favorites", "").andExpect(status().isOk())
                    .andExpect(jsonPath("$.metadataPartial").doesNotExist()).andExpect(jsonPath("$.items[0].gameId").exists())
                    .andExpect(jsonPath("$.items[0].entry").doesNotExist());
            listed(bearer, "completed", "").andExpect(status().isOk())
                    .andExpect(jsonPath("$.metadataPartial").doesNotExist()).andExpect(jsonPath("$.items[0].gameId").exists());
        }

        @Test void collectionRowsAndTotalUseOneDatabaseSnapshotDuringConcurrentWrite() throws Exception {
            writer.write(USER, GAME, com.ltz.library.domain.enums.LibraryAction.COLLECT);
            org.mockito.Mockito.doAnswer(invocation -> {
                Object snapshot = invocation.callRealMethod();
                try (var worker = java.util.concurrent.Executors.newSingleThreadExecutor()) {
                    worker.submit(() -> writer.write(USER, UUID.randomUUID(), com.ltz.library.domain.enums.LibraryAction.COLLECT))
                            .get(10, java.util.concurrent.TimeUnit.SECONDS);
                }
                return snapshot;
            }).when(entries).findCollection(USER, null, null, com.ltz.library.domain.enums.SortDirection.DESC, 0, 20);
            collectionGet(token(USER, List.of("PLAYER")), "").andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.totalElements").value(1));
            assertEquals(2, count());
        }

        @Test void collectionStatisticsCountOverlapOnceAndIgnoreIdempotentRepeatsAndOtherOwners() throws Exception {
            String bearer = token(USER, List.of("PLAYER"));
            fixture("00000000-0000-4000-8000-000000000001", UUID.randomUUID(), true, true);
            for (int i = 0; i < 2; i++) {
                mark(bearer, "favorite").andExpect(status().isOk());
                mark(bearer, "completed").andExpect(status().isOk());
            }
            org.mockito.Mockito.clearInvocations(catalog);
            stats(bearer).andExpect(status().isOk()).andExpect(jsonPath("$.totalGames").value(1))
                    .andExpect(jsonPath("$.favoriteGames").value(1)).andExpect(jsonPath("$.completedGames").value(1));
            clear(bearer, "favorite").andExpect(status().isNoContent());
            stats(bearer).andExpect(status().isOk()).andExpect(jsonPath("$.totalGames").value(1))
                    .andExpect(jsonPath("$.favoriteGames").value(0)).andExpect(jsonPath("$.completedGames").value(1));
            clear(bearer, "completed").andExpect(status().isNoContent());
            stats(bearer).andExpect(status().isOk()).andExpect(jsonPath("$.totalGames").value(1))
                    .andExpect(jsonPath("$.favoriteGames").value(0)).andExpect(jsonPath("$.completedGames").value(0));
            mvc.perform(delete("/api/v1/library/entries/" + GAME).header("Authorization", "Bearer " + bearer)).andExpect(status().isNoContent());
            stats(bearer).andExpect(status().isOk()).andExpect(jsonPath("$.totalGames").value(0));
            verifyNoInteractions(catalog);
        }

        @Test void emptyStatisticsAndIdentityRulesAreEnforcedWithoutExternalCalls() throws Exception {
            mvc.perform(get("/api/v1/library/entries/statistics")).andExpect(status().isUnauthorized());
            stats(token(USER, List.of())).andExpect(status().isForbidden());
            stats(sign(claims(), "svc+jwt")).andExpect(status().isUnauthorized());
            stats(token(USER, List.of("PLAYER"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalGames").value(0))
                    .andExpect(jsonPath("$.favoriteGames").value(0)).andExpect(jsonPath("$.completedGames").value(0));
            verifyNoInteractions(catalog);
        }

        private org.springframework.test.web.servlet.ResultActions stats(String bearer) throws Exception {
            return mvc.perform(get("/api/v1/library/entries/statistics?userId=" + UUID.randomUUID())
                    .header("X-User-Id", UUID.randomUUID().toString()).header("Authorization", "Bearer " + bearer));
        }

        @Test void fiftyDisplayedGamesUseOneBatchButTitleSortReadsAllFilteredGamesBeforePage() throws Exception {
            for (int i = 1; i <= 60; i++) fixture(String.format("00000000-0000-4000-8000-%012d", i), USER, true, false);
            var titles = new java.util.HashMap<UUID, String>();
            var rows = jdbc.queryForList("SELECT id,game_id FROM library_entries ORDER BY id");
            for (int i = 0; i < rows.size(); i++) titles.put((UUID) rows.get(i).get("game_id"), String.format("Game %03d", 60 - i));
            when(catalog.summaries(org.mockito.ArgumentMatchers.anyList())).thenAnswer(invocation -> {
                org.junit.jupiter.api.Assertions.assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
                List<UUID> ids = invocation.getArgument(0);
                return ids.stream().collect(java.util.stream.Collectors.toMap(id -> id,
                        id -> new com.ltz.library.application.port.GameSummary(id, titles.get(id), null, "INTERNAL", "INDEX_ONLY", "MAIN_GAME")));
            });
            String bearer = token(USER, List.of("PLAYER"));
            collectionGet(bearer, "?size=50").andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(50)).andExpect(jsonPath("$.totalElements").value(60));
            verify(catalog).summaries(org.mockito.ArgumentMatchers.argThat(ids -> ids.size() == 50));
            org.mockito.Mockito.clearInvocations(catalog);
            collectionGet(bearer, "?favorite=true&sort=TITLE&direction=DESC&size=1").andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].entry.id").value(rows.getFirst().get("id").toString()))
                    .andExpect(jsonPath("$.items[0].game.title").value("Game 060"))
                    .andExpect(jsonPath("$.totalElements").value(60));
            verify(catalog, times(2)).summaries(org.mockito.ArgumentMatchers.anyList());
            assertEquals(60, count());
        }

    }

    @org.junit.jupiter.api.Nested
    class CatalogResilienceTests {
        private org.springframework.test.web.client.MockRestServiceServer http;
        private com.ltz.library.infrastructure.client.GameServiceClient client;

        @BeforeEach void realCatalogAdapter() {
            var builder = org.springframework.web.client.RestClient.builder();
            http = org.springframework.test.web.client.MockRestServiceServer.bindTo(builder).build();
            client = new com.ltz.library.infrastructure.client.GameServiceClient(builder, "http://catalog.test");
            when(catalog.summaries(org.mockito.ArgumentMatchers.anyList())).thenAnswer(call -> {
                org.junit.jupiter.api.Assertions.assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
                return client.summaries(call.getArgument(0));
            });
            org.mockito.Mockito.doAnswer(call -> { client.requireAvailable(call.getArgument(0)); return null; })
                    .when(catalog).requireAvailable(org.mockito.ArgumentMatchers.any());
            jdbc.update("INSERT INTO library_entries(id,user_id,game_id,favorite,completed) VALUES (?,?,?,true,true)", UUID.randomUUID(), USER, GAME);
        }

        private void response(String body) {
            http.reset();
            http.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("http://catalog.test/api/v1/game/games/batch"))
                    .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist("Authorization"))
                    .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(body, MediaType.APPLICATION_JSON));
        }

        private String card(String title) {
            return "{\"items\":[{\"gameId\":\"" + GAME + "\",\"title\":" + title
                    + ",\"coverUrl\":\"https://cdn.example.test/cover.jpg\",\"origin\":\"EXTERNAL\",\"metadataCompleteness\":\"FULL\",\"gameType\":\"MAIN_GAME\"}],\"unavailable\":[]}";
        }

        private String unavailable() { return "{\"items\":[],\"unavailable\":[\"" + GAME + "\"]}"; }

        private org.springframework.test.web.servlet.ResultActions read(String query) throws Exception {
            return mvc.perform(get("/api/v1/library/entries" + query).header("Authorization", "Bearer " + token(USER, List.of("PLAYER"))));
        }

        @Test void lastKnownMetadataSurvivesArchiveOutageMissingTitleAndRecoveryWithoutChangingHistory() throws Exception {
            var before = jdbc.queryForMap("SELECT * FROM library_entries WHERE user_id=?", USER);
            response(card("\"Original\""));
            read("").andExpect(status().isOk()).andExpect(jsonPath("$.items[0].game.metadataSource").value("LIVE"))
                    .andExpect(jsonPath("$.metadataPartial").value(false));
            http.verify();
            var stored = jdbc.queryForMap("SELECT * FROM game_snapshots WHERE game_id=?", GAME);
            response(unavailable());
            read("?sort=TITLE&direction=ASC").andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].game.status").value("UNAVAILABLE"))
                    .andExpect(jsonPath("$.items[0].game.title").value("Original"))
                    .andExpect(jsonPath("$.items[0].game.metadataSource").value("LAST_KNOWN"))
                    .andExpect(jsonPath("$.items[0].game.metadataRefreshedAt").isNotEmpty())
                    .andExpect(jsonPath("$.metadataPartial").value(true));
            http.verify();
            http.reset();
            http.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.anything())
                    .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withException(new java.net.SocketTimeoutException("test timeout")));
            read("").andExpect(status().isOk()).andExpect(jsonPath("$.items[0].game.status").value("TEMPORARILY_UNAVAILABLE"))
                    .andExpect(jsonPath("$.items[0].game.title").value("Original"));
            http.verify();
            response(card("null"));
            read("").andExpect(status().isOk()).andExpect(jsonPath("$.items[0].game.status").value("AVAILABLE"))
                    .andExpect(jsonPath("$.items[0].game.metadataSource").value("LAST_KNOWN"))
                    .andExpect(jsonPath("$.metadataPartial").value(true));
            http.verify();
            assertEquals(stored, jdbc.queryForMap("SELECT * FROM game_snapshots WHERE game_id=?", GAME));
            response(card("\"Renamed\""));
            read("").andExpect(status().isOk()).andExpect(jsonPath("$.items[0].game.title").value("Renamed"))
                    .andExpect(jsonPath("$.items[0].game.metadataSource").value("LIVE"));
            http.verify();
            assertEquals(before, jdbc.queryForMap("SELECT * FROM library_entries WHERE user_id=?", USER));
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM game_snapshots", Integer.class));
        }

        @Test void firstSeenUnavailableGameUsesExplicitEmptyFallbackAndNoSnapshot() throws Exception {
            response(unavailable());
            read("").andExpect(status().isOk()).andExpect(jsonPath("$.items[0].game.title").isEmpty())
                    .andExpect(jsonPath("$.items[0].game.status").value("UNAVAILABLE"))
                    .andExpect(jsonPath("$.items[0].game.metadataSource").value("NONE"))
                    .andExpect(jsonPath("$.items[0].game.metadataRefreshedAt").isEmpty())
                    .andExpect(jsonPath("$.items[0].entry.favorite").value(true))
                    .andExpect(jsonPath("$.items[0].entry.completed").value(true));
            http.verify();
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM game_snapshots", Integer.class));
            assertEquals(1, count());
        }

        @Test void invalidBatchDoesNotPoisonSnapshotOrMakeArchivedGameWritable() throws Exception {
            response(card("\"Known\"")); read("").andExpect(status().isOk()); http.verify();
            var before = jdbc.queryForMap("SELECT * FROM library_entries");
            var snapshot = jdbc.queryForMap("SELECT * FROM game_snapshots");
            response("{\"items\":[{\"gameId\":\"" + UUID.randomUUID() + "\",\"title\":\"Wrong\"}],\"unavailable\":[]}");
            read("").andExpect(status().isOk()).andExpect(jsonPath("$.items[0].game.title").value("Known"))
                    .andExpect(jsonPath("$.items[0].game.status").value("TEMPORARILY_UNAVAILABLE"));
            http.verify();
            String bearer = token(USER, List.of("PLAYER"));
            for (String action : List.of("collect", "favorite", "completed")) {
                response(unavailable());
                if (action.equals("collect")) add(bearer, "{\"gameId\":\"" + GAME + "\"}").andExpect(status().isNotFound());
                else mark(bearer, action).andExpect(status().isNotFound());
                http.verify();
            }
            assertEquals(before, jdbc.queryForMap("SELECT * FROM library_entries"));
            assertEquals(snapshot, jdbc.queryForMap("SELECT * FROM game_snapshots"));
        }

        @Test void archivedEntryCanBeUnmarkedAndRemovedWithoutCatalogOrOtherOwnerLoss() throws Exception {
            UUID other = UUID.randomUUID();
            jdbc.update("INSERT INTO library_entries(id,user_id,game_id,favorite,completed) VALUES (?,?,?,true,true)", UUID.randomUUID(), other, GAME);
            String bearer = token(USER, List.of("PLAYER"));
            clear(bearer, "favorite").andExpect(status().isNoContent());
            listed(bearer, "completed", "").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.items[0].favorite").value(false));
            clear(bearer, "completed").andExpect(status().isNoContent());
            mvc.perform(get("/api/v1/library/entries/statistics").header("Authorization", "Bearer " + bearer))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalGames").value(1))
                    .andExpect(jsonPath("$.favoriteGames").value(0)).andExpect(jsonPath("$.completedGames").value(0));
            for (int i = 0; i < 2; i++) mvc.perform(delete("/api/v1/library/entries/" + GAME).header("Authorization", "Bearer " + bearer))
                    .andExpect(status().isNoContent());
            assertEquals(1, count());
            assertEquals(other, jdbc.queryForObject("SELECT user_id FROM library_entries", UUID.class));
            verifyNoInteractions(catalog);
            http.verify();
        }

        @Test void snapshotRefreshIgnoresOlderObservationsAndMissingTitles() {
            var snapshots = context.getBean(com.ltz.library.application.service.GameSnapshotService.class);
            Instant newer = Instant.parse("2026-10-30T12:00:01Z");
            var current = new com.ltz.library.application.port.GameSummary(GAME, "Newer", null, "INTERNAL", "FULL", "MAIN_GAME");
            snapshots.remember(List.of(current), newer);
            snapshots.remember(List.of(new com.ltz.library.application.port.GameSummary(GAME, "Older", null, null, null, null)), newer.minusSeconds(1));
            snapshots.remember(List.of(new com.ltz.library.application.port.GameSummary(GAME, null, null, null, null, null)), newer.plusSeconds(1));
            var saved = snapshots.read(List.of(GAME)).get(GAME);
            assertEquals("Newer", saved.getTitle());
            assertEquals(newer, saved.getRefreshedAt());
            assertEquals(0, saved.getVersion());
            assertEquals(java.util.Map.of(), snapshots.read(List.of()));
        }

        @Test void v41UpgradePreservesExistingCollectionAndCreatesOnlyPublicSnapshotTable() {
            var datasource = context.getBean(javax.sql.DataSource.class);
            try {
                var flyway = org.flywaydb.core.Flyway.configure().dataSource(datasource).defaultSchema("phase5_upgrade")
                        .locations("classpath:db/migration").target("41").load();
                flyway.migrate();
                jdbc.update("INSERT INTO phase5_upgrade.library_entries(id,user_id,game_id,favorite,completed) VALUES (?,?,?,true,true)", UUID.randomUUID(), USER, GAME);
                var original = jdbc.queryForMap("SELECT * FROM phase5_upgrade.library_entries");
                org.flywaydb.core.Flyway.configure().dataSource(datasource).defaultSchema("phase5_upgrade")
                        .locations("classpath:db/migration").load().migrate();
                assertEquals(original, jdbc.queryForMap("SELECT * FROM phase5_upgrade.library_entries"));
                assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM phase5_upgrade.game_snapshots", Integer.class));
                org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                        () -> jdbc.update("INSERT INTO phase5_upgrade.game_snapshots(game_id,title,refreshed_at,version) VALUES (?, 'Valid', CURRENT_TIMESTAMP, -1)", GAME));
            } finally { jdbc.execute("DROP SCHEMA IF EXISTS phase5_upgrade CASCADE"); }
        }

        @Test void fallbackTitlesParticipateInGlobalSortingAndUnknownTitlesStayLast() throws Exception {
            UUID alpha = UUID.randomUUID();
            UUID unknown = UUID.randomUUID();
            for (UUID id : List.of(alpha, unknown)) jdbc.update("INSERT INTO library_entries(id,user_id,game_id) VALUES (?,?,?)", UUID.randomUUID(), USER, id);
            var snapshots = context.getBean(com.ltz.library.application.service.GameSnapshotService.class);
            snapshots.remember(List.of(new com.ltz.library.application.port.GameSummary(GAME, "Zulu", null, null, null, null),
                    new com.ltz.library.application.port.GameSummary(alpha, "Alpha", null, null, null, null)), Instant.now());
            for (String direction : List.of("ASC", "DESC")) {
                response("{\"items\":[],\"unavailable\":[\"" + GAME + "\",\"" + alpha + "\",\"" + unknown + "\"]}");
                read("?sort=TITLE&direction=" + direction + "&size=1").andExpect(status().isOk())
                        .andExpect(jsonPath("$.items[0].game.title").value(direction.equals("ASC") ? "Alpha" : "Zulu"))
                        .andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.metadataPartial").value(true));
                http.verify();
                response("{\"items\":[],\"unavailable\":[\"" + GAME + "\",\"" + alpha + "\",\"" + unknown + "\"]}");
                read("?sort=TITLE&direction=" + direction + "&size=1&page=2").andExpect(status().isOk())
                        .andExpect(jsonPath("$.items[0].game.gameId").value(unknown.toString()))
                        .andExpect(jsonPath("$.items[0].game.metadataSource").value("NONE"));
                http.verify();
            }
            assertEquals(3, count());
        }

        @Test void concurrentSnapshotRefreshKeepsMostRecentObservedMetadata() throws Exception {
            var snapshots = context.getBean(com.ltz.library.application.service.GameSnapshotService.class);
            Instant base = Instant.parse("2026-10-30T12:00:00Z");
            try (var workers = java.util.concurrent.Executors.newFixedThreadPool(6)) {
                var tasks = new java.util.ArrayList<java.util.concurrent.Callable<Void>>();
                for (int i = 0; i < 16; i++) {
                    int sequence = i;
                    tasks.add(() -> {
                        snapshots.remember(List.of(new com.ltz.library.application.port.GameSummary(GAME, "Observed " + sequence, null, null, null, null)),
                                base.plusSeconds(sequence));
                        return null;
                    });
                }
                for (var future : workers.invokeAll(tasks)) future.get(15, java.util.concurrent.TimeUnit.SECONDS);
            }
            var saved = snapshots.read(List.of(GAME)).get(GAME);
            assertEquals("Observed 15", saved.getTitle());
            assertEquals(base.plusSeconds(15), saved.getRefreshedAt());
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM game_snapshots", Integer.class));
        }

        @Test void accountErasurePreservesSharedPublicMetadataAndOtherOwnersCollection() throws Exception {
            UUID other = UUID.randomUUID();
            jdbc.update("INSERT INTO library_entries(id,user_id,game_id,favorite,completed) VALUES (?,?,?,true,true)", UUID.randomUUID(), other, GAME);
            var snapshots = context.getBean(com.ltz.library.application.service.GameSnapshotService.class);
            snapshots.remember(List.of(new com.ltz.library.application.port.GameSummary(GAME, "Public title", null, null, null, null)), Instant.now());
            var saved = jdbc.queryForMap("SELECT * FROM game_snapshots");
            Instant now = Instant.now();
            String bearer = sign(new JWTClaimsSet.Builder().issuer("ltz-auth").audience("library-service").subject("svc:auth-service")
                    .issueTime(Date.from(now)).notBeforeTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(30)))
                    .jwtID(UUID.randomUUID().toString()).claim("scope", "account:erase").claim("account_id", USER.toString()), "svc+jwt");
            mvc.perform(delete("/internal/v1/accounts/" + USER).header("Authorization", "Bearer " + bearer)).andExpect(status().isNoContent());
            assertEquals(1, count());
            assertEquals(other, jdbc.queryForObject("SELECT user_id FROM library_entries", UUID.class));
            assertEquals(saved, jdbc.queryForMap("SELECT * FROM game_snapshots"));
            verifyNoInteractions(catalog);
        }
    }

    @org.junit.jupiter.api.Nested
    class PrivacyTests {
        private static final String PUBLIC = "/api/v1/library/users/";
        private static final String INTERNAL = "/internal/v1/library/users/";

        private void decision(com.ltz.library.domain.enums.LibraryAccessDecision value) {
            when(visibility.decide(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any())).thenReturn(value);
        }

        private org.springframework.test.web.servlet.ResultActions read(String path, String bearer) throws Exception {
            return mvc.perform(get(path).header("Authorization", "Bearer " + bearer));
        }

        private JWTClaimsSet.Builder service(String subject, String scope) {
            Instant now = Instant.now();
            return new JWTClaimsSet.Builder().issuer("ltz-auth").audience("library-service").subject(subject)
                    .issueTime(Date.from(now)).notBeforeTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(60)))
                    .jwtID(UUID.randomUUID().toString()).claim("scope", scope).claim("target_user_id", USER.toString())
                    .claim("viewer_id", UUID.randomUUID().toString());
        }

        @Test void ownProfileUsesVerifiedIdentityWithoutRemoteDecision() throws Exception {
            String bearer = token(USER, List.of("PLAYER"));
            read(PUBLIC + USER + "/entries", bearer).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "no-store"));
            read(PUBLIC + USER + "/entries/statistics", bearer).andExpect(status().isOk()).andExpect(jsonPath("$.totalGames").value(0));
            verifyNoInteractions(visibility, catalog);
        }

        @Test void deniedProfileDoesNotLeakCountsRowsOrExistence() throws Exception {
            fixture(UUID.randomUUID().toString(), USER, true, true);
            decision(com.ltz.library.domain.enums.LibraryAccessDecision.DENY);
            String bearer = token(UUID.randomUUID(), List.of("ADMIN"));
            String first = read(PUBLIC + USER + "/entries", bearer).andExpect(status().isForbidden()).andReturn().getResponse().getContentAsString();
            String empty = read(PUBLIC + UUID.randomUUID() + "/entries", bearer).andExpect(status().isForbidden()).andReturn().getResponse().getContentAsString();
            // ProblemDetail instance contains the path, but the status/detail never discloses data.
            assertEquals(new tools.jackson.databind.ObjectMapper().readTree(first).get("detail"),
                    new tools.jackson.databind.ObjectMapper().readTree(empty).get("detail"));
            read(PUBLIC + USER + "/entries/statistics", bearer).andExpect(status().isForbidden()).andExpect(jsonPath("$.totalGames").doesNotExist());
            verifyNoInteractions(entries, catalog);
        }

        @Test void unavailableNullAndThrowingPolicyFailClosedBeforeDataRead() throws Exception {
            String bearer = token(UUID.randomUUID(), List.of("PLAYER"));
            for (var value : com.ltz.library.domain.enums.LibraryAccessDecision.values()) {
                if (value != com.ltz.library.domain.enums.LibraryAccessDecision.UNAVAILABLE) continue;
                decision(value);
                read(PUBLIC + USER + "/entries", bearer).andExpect(status().isServiceUnavailable());
            }
            decision(null);
            read(PUBLIC + USER + "/entries/statistics", bearer).andExpect(status().isServiceUnavailable());
            when(visibility.decide(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any())).thenThrow(new IllegalStateException("internal-policy-detail"));
            read(PUBLIC + USER + "/entries", bearer).andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.detail").value("Erişim kararı geçici olarak alınamıyor."));
            verifyNoInteractions(entries, catalog);
        }

        @Test void allowedProfileReadsOnlyTargetAndUsesVerifiedViewer() throws Exception {
            UUID viewer = UUID.randomUUID();
            fixture(UUID.randomUUID().toString(), USER, false, false);
            fixture(UUID.randomUUID().toString(), viewer, true, true);
            decision(com.ltz.library.domain.enums.LibraryAccessDecision.ALLOW);
            read(PUBLIC + USER + "/entries/statistics?viewerId=" + USER, token(viewer, List.of("PLAYER")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalGames").value(1)).andExpect(jsonPath("$.favoriteGames").value(0));
            verify(visibility).decide(USER, viewer, com.ltz.library.domain.enums.LibraryAccessPurpose.PROFILE);
            read(PUBLIC + USER + "/entries", token(viewer, List.of("PLAYER")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.items[0].entry.favorite").value(false));
        }

        @Test void publicPathsRejectAnonymousRolesAndServiceTokens() throws Exception {
            for (String suffix : List.of("/entries", "/entries/statistics")) {
                mvc.perform(get(PUBLIC + USER + suffix)).andExpect(status().isUnauthorized());
                read(PUBLIC + USER + suffix, token(USER, List.of())).andExpect(status().isForbidden());
                read(PUBLIC + USER + suffix, sign(service("svc:user-service", "library:profile:read"), "svc+jwt"))
                        .andExpect(status().isUnauthorized());
            }
            verifyNoInteractions(visibility, entries, catalog);
        }

        @Test void internalProfileRequiresExactCallerScopeTargetAndSignedViewer() throws Exception {
            String route = INTERNAL + USER + "/entries/statistics";
            String scope = "library:profile:read";
            decision(com.ltz.library.domain.enums.LibraryAccessDecision.ALLOW);
            read(route, sign(service("svc:user-service", scope), "svc+jwt")).andExpect(status().isOk());
            read(INTERNAL + USER + "/entries", sign(service("svc:user-service", scope), "svc+jwt"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
            for (var invalid : List.of(service("svc:recommendation-service", scope), service("svc:user-service", "account:erase"),
                    service("svc:user-service", scope).claim("target_user_id", UUID.randomUUID().toString()),
                    service("svc:user-service", scope).claim("target_user_id", null),
                    service("svc:user-service", scope).claim("viewer_id", null),
                    service("svc:user-service", scope).claim("viewer_id", "1-1-1-1-1"))) {
                read(route, sign(invalid, "svc+jwt")).andExpect(status().isForbidden());
            }
            verify(visibility, times(2)).decide(org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        }

        @Test void internalReadRejectsUserD4AndInvalidServiceContracts() throws Exception {
            String route = INTERNAL + USER + "/recommendation-signals";
            String scope = "library:recommendation:read";
            mvc.perform(get(route)).andExpect(status().isUnauthorized());
            read(route, token(USER, List.of("ADMIN"))).andExpect(status().isUnauthorized());
            read(route, sign(service("svc:auth-service", "account:erase"), "svc+jwt")).andExpect(status().isForbidden());
            for (var invalid : List.of(service("svc:recommendation-service", scope).audience("user-service"),
                    service("svc:recommendation-service", scope).issuer("other"),
                    service("svc:recommendation-service", scope).jwtID(null),
                    service("svc:recommendation-service", scope).expirationTime(Date.from(Instant.now().plusSeconds(300))))) {
                read(route, sign(invalid, "svc+jwt")).andExpect(status().isUnauthorized());
            }
            verifyNoInteractions(visibility, entries, catalog);
        }

        @Test void recommendationReturnsOnlyPermittedSignalsWithStablePaging() throws Exception {
            for (int i = 1; i <= 4; i++) fixture(String.format("00000000-0000-4000-8000-%012d", i), USER, i % 2 == 0, i > 2);
            fixture(UUID.randomUUID().toString(), UUID.randomUUID(), true, true);
            decision(com.ltz.library.domain.enums.LibraryAccessDecision.ALLOW);
            String bearer = sign(service("svc:recommendation-service", "library:recommendation:read"), "svc+jwt");
            String route = INTERNAL + USER + "/recommendation-signals";
            read(route + "?size=2", bearer).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                    .andExpect(jsonPath("$.items.length()").value(2)).andExpect(jsonPath("$.items[0].id").doesNotExist())
                    .andExpect(jsonPath("$.items[0].userId").doesNotExist()).andExpect(jsonPath("$.items[0].addedAt").doesNotExist())
                    .andExpect(jsonPath("$.items[0].favorite").value(true)).andExpect(jsonPath("$.items[0].completed").value(true));
            read(route + "?size=2&page=1", bearer).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
            read(route + "?page=2147483647", bearer).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
            verifyNoInteractions(catalog);
            verify(visibility, times(3)).decide(USER, USER,
                    com.ltz.library.domain.enums.LibraryAccessPurpose.RECOMMENDATION);
        }

        @Test void revokedOrUnavailableRecommendationPermissionNeverUsesPreviousAllow() throws Exception {
            String bearer = sign(service("svc:recommendation-service", "library:recommendation:read"), "svc+jwt");
            String route = INTERNAL + USER + "/recommendation-signals";
            decision(com.ltz.library.domain.enums.LibraryAccessDecision.ALLOW);
            read(route, bearer).andExpect(status().isOk());
            org.mockito.Mockito.clearInvocations(entries);
            decision(com.ltz.library.domain.enums.LibraryAccessDecision.DENY);
            read(route, bearer).andExpect(status().isForbidden());
            decision(com.ltz.library.domain.enums.LibraryAccessDecision.UNAVAILABLE);
            read(route, bearer).andExpect(status().isServiceUnavailable());
            read(route, sign(service("svc:user-service", "library:recommendation:read"), "svc+jwt")).andExpect(status().isForbidden());
            verifyNoInteractions(entries, catalog);
        }

        @Test void allNewPagedPathsValidateBounds() throws Exception {
            String userToken = token(USER, List.of("PLAYER"));
            for (String query : List.of("?size=0", "?size=51", "?page=-1", "?page=bad")) {
                read(PUBLIC + USER + "/entries" + query, userToken).andExpect(status().isBadRequest());
                read(INTERNAL + USER + "/entries" + query, sign(service("svc:user-service", "library:profile:read"), "svc+jwt"))
                        .andExpect(status().isBadRequest());
                read(INTERNAL + USER + "/recommendation-signals" + query,
                        sign(service("svc:recommendation-service", "library:recommendation:read"), "svc+jwt"))
                        .andExpect(status().isBadRequest());
            }
            verifyNoInteractions(visibility, entries, catalog);
        }

        @Test void concurrentAddMarkAndDeletePreservesUniqueOwnerRows() throws Exception {
            UUID other = UUID.randomUUID();
            String bearer = token(USER, List.of("PLAYER"));
            add(token(other, List.of("PLAYER")), "{\"gameId\":\"" + GAME + "\"}").andExpect(status().isOk());
            try (var workers = java.util.concurrent.Executors.newFixedThreadPool(8)) {
                var tasks = new java.util.ArrayList<java.util.concurrent.Callable<Void>>();
                for (int i = 0; i < 32; i++) {
                    int action = i % 4;
                    tasks.add(() -> {
                        if (action == 0) add(bearer, "{\"gameId\":\"" + GAME + "\"}").andExpect(status().isOk());
                        else if (action == 1) mark(bearer, "favorite").andExpect(status().isOk());
                        else if (action == 2) mark(bearer, "completed").andExpect(status().isOk());
                        else mvc.perform(delete("/api/v1/library/entries/" + GAME).header("Authorization", "Bearer " + bearer))
                                .andExpect(status().isNoContent());
                        return null;
                    });
                }
                for (var future : workers.invokeAll(tasks)) future.get(15, java.util.concurrent.TimeUnit.SECONDS);
            }
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM (SELECT user_id,game_id FROM library_entries GROUP BY user_id,game_id HAVING count(*)>1) duplicates", Integer.class));
            mvc.perform(delete("/api/v1/library/entries/" + GAME).header("Authorization", "Bearer " + bearer)).andExpect(status().isNoContent());
            assertEquals(1, count());
            assertEquals(other, jdbc.queryForObject("SELECT user_id FROM library_entries", UUID.class));
        }
    }

    private org.springframework.test.web.servlet.ResultActions clear(String bearer, String flag) throws Exception {
        return mvc.perform(delete("/api/v1/library/entries/" + GAME + "/" + flag).header("Authorization", "Bearer " + bearer));
    }

    private org.springframework.test.web.servlet.ResultActions listed(String bearer, String list, String query) throws Exception {
        return mvc.perform(get("/api/v1/library/entries/" + list + query).header("Authorization", "Bearer " + bearer));
    }

    private void fixture(String id, UUID owner, boolean favorite, boolean completed) {
        jdbc.update("INSERT INTO library_entries(id,user_id,game_id,favorite,completed,added_at,updated_at)"
                + " VALUES (?,?,?,?,?,'2026-10-30T12:00:00Z','2026-10-30T12:00:00Z')",
                UUID.fromString(id), owner, UUID.randomUUID(), favorite, completed);
    }

    private org.springframework.test.web.servlet.ResultActions add(String bearer, String body) throws Exception {
        return mvc.perform(post("/api/v1/library/entries").header("Authorization", "Bearer " + bearer)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private int count() { return jdbc.queryForObject("SELECT count(*) FROM library_entries", Integer.class); }

    static String token(UUID user, Object roles) throws Exception {
        Instant now = Instant.now();
        return sign(new JWTClaimsSet.Builder().issuer("ltz-auth").audience("ltz-api").subject(user.toString())
                .issueTime(Date.from(now)).notBeforeTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(60)))
                .claim("roles", roles), "at+jwt");
    }

    private static JWTClaimsSet.Builder claims() {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder().issuer("ltz-auth").audience("ltz-api").subject(USER.toString())
                .issueTime(Date.from(now)).notBeforeTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(60)))
                .claim("roles", List.of("PLAYER"));
    }

    static String sign(JWTClaimsSet.Builder claims, String type) throws Exception {
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).type(new JOSEObjectType(type)).keyID("library-api").build(), claims.build());
        jwt.sign(new RSASSASigner(KEYS.getPrivate()));
        return jwt.serialize();
    }

    private static KeyPair keys() {
        try { var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(3072); return generator.generateKeyPair(); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }

    private static HttpServer jwks() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            var key = new RSAKey.Builder((java.security.interfaces.RSAPublicKey) KEYS.getPublic()).keyID("library-api").build();
            var issuerKey = new RSAKey.Builder((java.security.interfaces.RSAPublicKey) KEYS.getPublic()).keyIDFromThumbprint().build();
            byte[] body = new JWKSet(List.of(key, issuerKey)).toString().getBytes(StandardCharsets.UTF_8);
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

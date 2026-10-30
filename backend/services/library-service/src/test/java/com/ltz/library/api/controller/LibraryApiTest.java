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

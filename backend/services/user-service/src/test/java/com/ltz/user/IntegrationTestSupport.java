package com.ltz.user;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;

import jakarta.servlet.Filter;

/**
 * Gerçek PostgreSQL 17 (Compose ile aynı imaj) üzerinde, Flyway migration'ları uygulanmış tam uygulama bağlamı ve
 * Auth Service yerine geçen yerel bir JWKS sunucusu. Token'lar her çalıştırmada üretilen RSA anahtarıyla gerçekten
 * imzalanır; böylece JWT doğrulaması (imza, iss, aud, typ, exp) sahte yardımcılarla atlanmaz. Repoda gerçek anahtar yoktur.
 */
// Geliştiricinin kabuğundaki SPRING_PROFILES_ACTIVE (ör. .env.dev yüklenmişse dev) testlere sızmasın.
@ActiveProfiles("test")
@SpringBootTest
public abstract class IntegrationTestSupport {

    protected static final String ISSUER = "ltz-auth";
    protected static final String AUDIENCE = "ltz-api";
    protected static final String KEY_ID = "user-test-key";
    protected static final JOSEObjectType ACCESS_TOKEN_TYPE = new JOSEObjectType("at+jwt");
    protected static final JOSEObjectType SERVICE_TOKEN_TYPE = new JOSEObjectType("svc+jwt");
    protected static final String SERVICE_AUDIENCE = "user-service";
    protected static final String ERASE_SCOPE = "account:erase";
    protected static final String EVENTS_SCOPE = "events:ingest";
    protected static final String AUTH_SUBJECT = "svc:auth-service";

    private static final int TEST_RSA_BITS = 2048;
    private static final long TOKEN_TTL_SECONDS = 300;

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    /** Test görsellerinin yazıldığı geçici klasör. */
    protected static final Path MEDIA_ROOT = createMediaRoot();
    /** Her test çalıştırmasında üretilen imza anahtarı. */
    protected static final RSAKey SIGNING_KEY = generateKey();
    private static final HttpServer JWKS_SERVER;
    /** JWKS sunucusunun yayımladığı açık anahtarlar. */
    private static final List<RSAKey> PUBLISHED_KEYS = new CopyOnWriteArrayList<>();

    static {
        POSTGRES.start();
        PUBLISHED_KEYS.add(SIGNING_KEY.toPublicJWK());
        JWKS_SERVER = startJwksServer();
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    protected JdbcTemplate jdbc;

    protected MockMvc mockMvc;

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("ltz.user.auth.jwks-uri",
                () -> "http://127.0.0.1:" + JWKS_SERVER.getAddress().getPort() + "/jwks.json");
        registry.add("ltz.user.auth.issuer", () -> ISSUER);
        registry.add("ltz.user.auth.audience", () -> AUDIENCE);
        registry.add("ltz.user.auth.service-audience", () -> SERVICE_AUDIENCE);
        // Görseller her test çalıştırmasında geçici klasöre yazılır; zamanlanmış temizleyici testlerde kapalıdır
        // (temizleyici doğrudan çağrılarak sınanır).
        registry.add("ltz.user.media.root", MEDIA_ROOT::toString);
        registry.add("ltz.user.media.cleanup-enabled", () -> "false");
    }

    /** Gerçek sunucudaki filtre sırası: gövde sınırı, ardından Spring Security zinciri. */
    @BeforeEach
    void setUpMockMvc() {
        Filter bodySizeLimit = context.getBean("requestBodySizeLimitFilter", FilterRegistrationBean.class).getFilter();
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(bodySizeLimit)
                .apply(springSecurity())
                .build();
    }

    protected static UUID newUser() {
        return UUID.randomUUID();
    }

    /** Auth'un verdiği biçimde (RS256, typ at+jwt, iss, aud, sub, roles) geçerli bir access token'ın Authorization değeri. */
    protected static String bearer(UUID userId) {
        return "Bearer " + accessToken(userId, UnaryOperator.identity());
    }

    protected static String accessToken(UUID userId, UnaryOperator<JWTClaimsSet.Builder> claimsChange) {
        return sign(SIGNING_KEY, ACCESS_TOKEN_TYPE, claimsChange.apply(validClaims(userId)).build());
    }

    protected static JWTClaimsSet.Builder validClaims(UUID userId) {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .subject(userId.toString())
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .notBeforeTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(TOKEN_TTL_SECONDS)))
                .claim("roles", List.of("PLAYER"));
    }

    /**
     * Auth'un D4 sözleşmesindeki biçimde (svc+jwt, sub=svc:auth-service, aud=hedef servis, scope=account:erase,
     * account_id, jti, ≤ 60 sn) {@code accountId} hesabının silinmesi için geçerli servis token'ı.
     */
    protected static String serviceToken(UUID accountId, UnaryOperator<JWTClaimsSet.Builder> claimsChange) {
        return sign(SIGNING_KEY, SERVICE_TOKEN_TYPE, claimsChange.apply(validServiceClaims(accountId)).build());
    }

    protected static JWTClaimsSet.Builder validServiceClaims(UUID accountId) {
        return serviceClaims(AUTH_SUBJECT, ERASE_SCOPE).claim("account_id", accountId.toString());
    }

    /** Auth'un olay teslimindeki token: scope=events:ingest, account_id = olayın hesabı. */
    protected static String eventToken(UUID accountId, UnaryOperator<JWTClaimsSet.Builder> claimsChange) {
        JWTClaimsSet.Builder claims = serviceClaims(AUTH_SUBJECT, EVENTS_SCOPE).claim("account_id", accountId.toString());
        return sign(SIGNING_KEY, SERVICE_TOKEN_TYPE, claimsChange.apply(claims).build());
    }

    protected static JWTClaimsSet.Builder serviceClaims(String subject, String scope) {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(SERVICE_AUDIENCE)
                .subject(subject)
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .notBeforeTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(60)))
                .claim("scope", scope);
    }

    protected static String sign(RSAKey key, JOSEObjectType type, JWTClaimsSet claims) {
        try {
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).type(type).build();
            SignedJWT jwt = new SignedJWT(header, claims);
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException(exception);
        }
    }

    protected int profileCount(UUID userId) {
        return jdbc.queryForObject("select count(*) from user_profiles where user_id = ?", Integer.class, userId);
    }

    protected void insertProfile(UUID userId) {
        jdbc.update("""
                insert into user_profiles (user_id, display_name, bio, account_status, created_at, updated_at, version)
                values (?, 'Test Oyuncu', 'merhaba', 'ACTIVE', now(), now(), 0)""", userId);
    }

    /** Kullanıcı adı ve görünen adı olan, verilen durumda bir profil açar. */
    protected void insertNamedProfile(UUID userId, String username, String displayName, String status) {
        jdbc.update("""
                insert into user_profiles (user_id, username_snapshot, username_normalized, display_name,
                                           display_name_normalized, bio, country, player_types, account_status,
                                           created_at, updated_at, version)
                values (?, ?, lower(?), ?, lower(?), 'biyografi', 'TR', '{CASUAL}', ?, now(), now(), 0)""",
                userId, username, username, displayName, displayName, status);
    }

    protected void insertPrivacy(UUID userId, String friends, String library, String hardware, String performance,
                                 String guest) {
        jdbc.update("""
                insert into privacy_settings (user_id, friends_visibility, library_visibility, hardware_visibility,
                                              performance_visibility, guest_profile_access, updated_at, version)
                values (?, ?, ?, ?, ?, ?, now(), 0)""", userId, friends, library, hardware, performance, guest);
    }

    /** Social yansımasına aktif arkadaşlık yazar (olay yolu {@code InternalEventTest} içinde sınanır). */
    protected void befriend(UUID first, UUID second) {
        jdbc.update("insert into social_friendships (user_low_id, user_high_id, active, changed_at) values (?, ?, true, now())",
                first, second);
    }

    protected void block(UUID blocker, UUID blocked) {
        jdbc.update("insert into social_blocks (blocker_id, blocked_id, active, changed_at) values (?, ?, true, now())",
                blocker, blocked);
    }

    private static Path createMediaRoot() {
        try {
            Path root = Files.createTempDirectory("ltz-user-media-test-");
            root.toFile().deleteOnExit();
            return root;
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static RSAKey generateKey() {
        try {
            return new RSAKeyGenerator(TEST_RSA_BITS).keyID(KEY_ID).generate();
        } catch (JOSEException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static HttpServer startJwksServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/jwks.json", exchange -> {
                byte[] jwks = new JWKSet(new ArrayList<JWK>(PUBLISHED_KEYS)).toString()
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
                exchange.sendResponseHeaders(200, jwks.length);
                try (OutputStream body = exchange.getResponseBody()) {
                    body.write(jwks);
                }
            });
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}

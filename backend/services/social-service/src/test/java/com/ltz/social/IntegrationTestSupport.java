package com.ltz.social;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.jayway.jsonpath.JsonPath;
import com.ltz.social.api.controller.SocialApiPaths;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
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
    protected static final String KEY_ID = "social-test-key";
    protected static final JOSEObjectType ACCESS_TOKEN_TYPE = new JOSEObjectType("at+jwt");

    private static final int TEST_RSA_BITS = 2048;
    private static final long TOKEN_TTL_SECONDS = 300;

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    /** Her test çalıştırmasında üretilen imza anahtarı. */
    protected static final RSAKey SIGNING_KEY = generateKey();
    private static final HttpServer JWKS_SERVER;

    static {
        POSTGRES.start();
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
        registry.add("ltz.social.auth.jwks-uri",
                () -> "http://127.0.0.1:" + JWKS_SERVER.getAddress().getPort() + "/jwks.json");
        registry.add("ltz.social.auth.issuer", () -> ISSUER);
        registry.add("ltz.social.auth.audience", () -> AUDIENCE);
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

    protected static String sign(RSAKey key, JOSEObjectType type, JWTClaimsSet claims) {
        try {
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY_ID).type(type).build();
            SignedJWT jwt = new SignedJWT(header, claims);
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException(exception);
        }
    }

    protected ResultActions sendRequest(UUID sender, UUID receiver) throws Exception {
        return mockMvc.perform(post(SocialApiPaths.FRIEND_REQUESTS)
                .header(HttpHeaders.AUTHORIZATION, bearer(sender))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"receiverId":"%s"}
                        """.formatted(receiver)));
    }

    /** İsteği gönderir (201 beklenir) ve oluşan isteğin kimliğini döner. */
    protected UUID sendRequestAndGetId(UUID sender, UUID receiver) throws Exception {
        String body = sendRequest(sender, receiver).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    protected ResultActions act(String pathTemplate, UUID actor, UUID requestId) throws Exception {
        return mockMvc.perform(post(pathTemplate, requestId).header(HttpHeaders.AUTHORIZATION, bearer(actor)));
    }

    protected ResultActions list(String path, UUID user, String query) throws Exception {
        return mockMvc.perform(get(path + query).header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    protected int friendshipCount() {
        return jdbc.queryForObject("select count(*) from friendships", Integer.class);
    }

    protected boolean areFriends(UUID first, UUID second) {
        return jdbc.queryForObject("select count(*) from friendships where "
                + "(user_low_id = ? and user_high_id = ?) or (user_low_id = ? and user_high_id = ?)",
                Integer.class, first, second, second, first) == 1;
    }

    protected String requestStatus(UUID requestId) {
        return jdbc.queryForObject("select status from friend_requests where id = ?", String.class, requestId);
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
            byte[] jwks = new JWKSet(SIGNING_KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/jwks.json", exchange -> {
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

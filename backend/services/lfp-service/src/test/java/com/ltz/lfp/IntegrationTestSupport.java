package com.ltz.lfp;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.jayway.jsonpath.JsonPath;
import com.ltz.lfp.api.controller.LfpApiPaths;
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
import tools.jackson.databind.ObjectMapper;

/**
 * Gerçek PostgreSQL 17 (Compose ile aynı imaj) üzerinde, Flyway migration'ları uygulanmış tam uygulama bağlamı ve
 * Auth Service yerine geçen yerel bir JWKS sunucusu. Token'lar her çalıştırmada üretilen RSA anahtarıyla gerçekten
 * imzalanır; JWT doğrulaması sahte yardımcılarla atlanmaz. game-service {@link ScriptedGameCatalog} ile taklit edilir
 * (gerçek istemci ayrıca MockRestServiceServer ile sınanır). Repoda gerçek anahtar yoktur.
 */
// Geliştiricinin kabuğundaki SPRING_PROFILES_ACTIVE (ör. .env.dev yüklenmişse dev) testlere sızmasın.
@ActiveProfiles("test")
@SpringBootTest
@Import(ScriptedGameCatalog.Configuration.class)
@TestPropertySource(properties = "ltz.lfp.games.adapter=scripted")
public abstract class IntegrationTestSupport {

    protected static final String ISSUER = "ltz-auth";
    protected static final String AUDIENCE = "ltz-api";
    protected static final String KEY_ID = "lfp-test-key";
    protected static final JOSEObjectType ACCESS_TOKEN_TYPE = new JOSEObjectType("at+jwt");
    protected static final JOSEObjectType SERVICE_TOKEN_TYPE = new JOSEObjectType("svc+jwt");

    private static final int TEST_RSA_BITS = 2048;
    private static final long TOKEN_TTL_SECONDS = 300;

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    /** Her test çalıştırmasında üretilen imza anahtarı. */
    protected static final RSAKey SIGNING_KEY = generateKey();
    private static final HttpServer JWKS_SERVER;
    /** JWKS sunucusunun yayımladığı açık anahtarlar; anahtar dönüşümü testleri buraya ekler. */
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

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected ScriptedGameCatalog games;

    protected MockMvc mockMvc;

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("ltz.lfp.auth.jwks-uri",
                () -> "http://127.0.0.1:" + JWKS_SERVER.getAddress().getPort() + "/jwks.json");
        registry.add("ltz.lfp.auth.issuer", () -> ISSUER);
        registry.add("ltz.lfp.auth.audience", () -> AUDIENCE);
    }

    /** Gerçek sunucudaki filtre sırası: gövde sınırı, ardından Spring Security zinciri. */
    @BeforeEach
    void setUpMockMvc() {
        Filter bodySizeLimit = context.getBean("requestBodySizeLimitFilter", FilterRegistrationBean.class).getFilter();
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(bodySizeLimit)
                .apply(springSecurity())
                .build();
        games.reset();
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

    /** JWKS sunucusuna yeni bir açık anahtar ekler (Auth'un anahtar dönüşümünü taklit eder). */
    protected static void publishKey(RSAKey key) {
        PUBLISHED_KEYS.add(key.toPublicJWK());
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

    // ---------------------------------------------------------------- ilan yardımcıları

    /** Tüm zorunlu alanları dolu, geçerli bir ilan gövdesi (isteğe bağlı alanlar yok). Testler alanları değiştirir. */
    protected static Map<String, Object> listingBody(UUID gameId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("gameId", gameId.toString());
        body.put("platform", "PC");
        body.put("roles", List.of("Support"));
        body.put("playersNeeded", 2);
        body.put("micRequired", true);
        body.put("description", "Akşam rahat ranked, takım oyununa önem veren oyuncular arıyoruz.");
        return body;
    }

    protected ResultActions createListing(UUID owner, Map<String, Object> body) throws Exception {
        return createListingJson(owner, objectMapper.writeValueAsString(body));
    }

    protected ResultActions createListingJson(UUID owner, String json) throws Exception {
        return mockMvc.perform(post(LfpApiPaths.LISTINGS)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    /** Yeni bir oyun için geçerli ilan açar (201 beklenir) ve ilanın kimliğini döner. */
    protected UUID openListing(UUID owner) throws Exception {
        return openListing(owner, listingBody(games.addGame("Test Game")));
    }

    protected UUID openListing(UUID owner, Map<String, Object> body) throws Exception {
        String response = createListing(owner, body).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.id"));
    }

    protected int listingCount(UUID owner) {
        return jdbc.queryForObject("select count(*) from lfp_listings where owner_id = ?", Integer.class, owner);
    }

    protected String listingStatus(UUID listingId) {
        return jdbc.queryForObject("select status from lfp_listings where id = ?", String.class, listingId);
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

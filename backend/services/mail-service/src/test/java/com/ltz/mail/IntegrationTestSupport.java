package com.ltz.mail;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 * Gerçek PostgreSQL 17 (Compose ile aynı imaj) üzerinde, Flyway migration'ları uygulanmış tam uygulama bağlamı ve Auth
 * Service yerine geçen yerel bir JWKS sunucusu. Token'lar her çalıştırmada üretilen RSA anahtarıyla gerçekten imzalanır;
 * böylece JWT doğrulaması (imza, iss, aud, typ, sub, exp) sahte yardımcılarla atlanmaz. SMTP yerine
 * {@link RecordingMailSender} kullanılır; zamanlanmış işleyiciler kapalıdır (servisler doğrudan çağrılır).
 */
// Geliştiricinin kabuğundaki SPRING_PROFILES_ACTIVE (ör. .env.dev yüklenmişse dev) testlere sızmasın.
@ActiveProfiles("test")
@SpringBootTest
@Import(TestMailSenderConfig.class)
public abstract class IntegrationTestSupport {

    protected static final String ISSUER = "ltz-auth";
    protected static final String SERVICE_AUDIENCE = "mail-service";
    protected static final String KEY_ID = "mail-test-key";
    protected static final JOSEObjectType SERVICE_TOKEN_TYPE = new JOSEObjectType("svc+jwt");
    protected static final JOSEObjectType ACCESS_TOKEN_TYPE = new JOSEObjectType("at+jwt");
    protected static final String AUTH_SUBJECT = "svc:auth-service";
    protected static final String MAIL_SEND_SCOPE = "mail:send";
    protected static final String ERASE_SCOPE = "account:erase";
    protected static final String ALLOWED_LINK_HOST = "app.ltz.test";
    protected static final String RESET_LINK = "https://" + ALLOWED_LINK_HOST + "/reset?token=abc";

    private static final int TEST_RSA_BITS = 2048;
    private static final byte[] PAYLOAD_KEY_BYTES = new byte[32];

    static {
        Arrays.fill(PAYLOAD_KEY_BYTES, (byte) 7);
    }

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

    @Autowired
    protected RecordingMailSender mailSender;

    protected MockMvc mockMvc;

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("ltz.mail.auth.jwks-uri",
                () -> "http://127.0.0.1:" + JWKS_SERVER.getAddress().getPort() + "/jwks.json");
        registry.add("ltz.mail.auth.issuer", () -> ISSUER);
        registry.add("ltz.mail.auth.service-audience", () -> SERVICE_AUDIENCE);
        registry.add("ltz.mail.crypto.payload-key", () -> Base64.getEncoder().encodeToString(PAYLOAD_KEY_BYTES));
        registry.add("ltz.mail.link.allowed-hosts", () -> ALLOWED_LINK_HOST);
        // İşleyiciler testlerde kapalıdır: servisler doğrudan çağrılır, zamanlama testleri kararsızlaştırmaz.
        registry.add("ltz.mail.worker.enabled", () -> "false");
    }

    /** Gerçek sunucudaki filtre sırası: gövde sınırı, ardından Spring Security zinciri. */
    @BeforeEach
    void setUpMockMvc() {
        Filter bodySizeLimit = context.getBean("requestBodySizeLimitFilter", FilterRegistrationBean.class).getFilter();
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(bodySizeLimit)
                .apply(springSecurity())
                .build();
        jdbc.update("delete from mail_messages");
        mailSender.reset();
    }

    protected static UUID newAccount() {
        return UUID.randomUUID();
    }

    /** Auth'un verdiği biçimde (svc+jwt, sub=svc:auth-service, aud=mail-service, scope, account_id, ≤ 60 sn) token. */
    protected static String bearer(UUID accountId, String scope) {
        return "Bearer " + serviceToken(accountId, scope, UnaryOperator.identity());
    }

    protected static String serviceToken(UUID accountId, String scope, UnaryOperator<JWTClaimsSet.Builder> change) {
        return sign(SIGNING_KEY, SERVICE_TOKEN_TYPE, change.apply(serviceClaims(accountId, scope)).build());
    }

    protected static JWTClaimsSet.Builder serviceClaims(UUID accountId, String scope) {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(SERVICE_AUDIENCE)
                .subject(AUTH_SUBJECT)
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .notBeforeTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(30)))
                .claim("scope", scope)
                .claim("account_id", accountId.toString());
    }

    /** Kullanıcı token'ı ({@code at+jwt}): Mail Service hiçbir uçta kabul etmemelidir. */
    protected static String userAccessToken(UUID accountId) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience("ltz-api")
                .subject(accountId.toString())
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .notBeforeTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(60)))
                .claim("roles", List.of("PLAYER"))
                .build();
        return sign(SIGNING_KEY, ACCESS_TOKEN_TYPE, claims);
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

    protected static String verificationBody(UUID key, UUID accountId, String email, String code) {
        return """
                {"idempotencyKey":"%s","accountId":"%s","recipientEmail":"%s","code":"%s","validitySeconds":600}"""
                .formatted(key, accountId, email, code);
    }

    protected static String resetBody(UUID key, UUID accountId, String email, String link) {
        return """
                {"idempotencyKey":"%s","accountId":"%s","recipientEmail":"%s","resetLink":"%s","validitySeconds":900}"""
                .formatted(key, accountId, email, link);
    }

    protected static String notificationBody(UUID key, UUID accountId, String email, String type) {
        return """
                {"idempotencyKey":"%s","accountId":"%s","recipientEmail":"%s","type":"%s","occurredAt":"%s"}"""
                .formatted(key, accountId, email, type, Instant.now());
    }

    protected Map<String, Object> messageByKey(UUID idempotencyKey) {
        return jdbc.queryForMap("select * from mail_messages where idempotency_key = ?", idempotencyKey);
    }

    protected int messageCount() {
        Integer count = jdbc.queryForObject("select count(*) from mail_messages", Integer.class);
        return count == null ? 0 : count;
    }

    /** Kaydı hemen işlenebilir yapar (geri çekilme beklemeden). */
    protected void makeDue(UUID idempotencyKey) {
        jdbc.update("update mail_messages set next_attempt_at = now() - interval '1 second' where idempotency_key = ?",
                idempotencyKey);
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
                byte[] jwks = new JWKSet(new ArrayList<JWK>(List.of(SIGNING_KEY.toPublicJWK()))).toString()
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

package com.ltz.messaging;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

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
import com.ltz.messaging.api.controller.MessagingApiPaths;
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
 * Gerçek PostgreSQL 17 (Compose ile aynı imaj) üzerinde Flyway migration'ları uygulanmış tam uygulama bağlamı ve Auth
 * Service yerine geçen yerel bir JWKS sunucusu. Token'lar her çalıştırmada üretilen RSA anahtarıyla gerçekten
 * imzalanır; JWT doğrulaması sahte yardımcılarla atlanmaz. Social'ın engel sorgusu {@link ScriptedBlockCheck} ile
 * taklit edilir (gerçek istemci {@code SocialBlockCheckClientTest}'te sınanır). Repoda gerçek anahtar yoktur.
 */
// Geliştiricinin kabuğundaki SPRING_PROFILES_ACTIVE (ör. dev) testlere sızmasın.
@ActiveProfiles("test")
@SpringBootTest
@Import(ScriptedBlockCheck.Configuration.class)
// Alt sınıf gerçek adaptörü (social) seçebilsin diye dinamik özellik değil, ezilebilir test özelliğidir.
@TestPropertySource(properties = "ltz.messaging.blocks.adapter=scripted")
public abstract class IntegrationTestSupport {

    protected static final String ISSUER = "ltz-auth";
    protected static final String AUDIENCE = "ltz-api";
    protected static final JOSEObjectType ACCESS_TOKEN_TYPE = new JOSEObjectType("at+jwt");

    private static final long TOKEN_TTL_SECONDS = 300;

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    /** Her test çalıştırmasında üretilen imza anahtarı. */
    protected static final RSAKey SIGNING_KEY = generateKey("messaging-test-key");
    private static final HttpServer JWKS_SERVER;

    static {
        POSTGRES.start();
        JWKS_SERVER = startJwksServer();
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    protected JdbcTemplate jdbc;

    /** Gerçek adaptörü seçen testlerde yoktur. */
    @Autowired(required = false)
    protected ScriptedBlockCheck blocks;

    protected MockMvc mockMvc;

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("ltz.messaging.auth.jwks-uri",
                () -> "http://127.0.0.1:" + JWKS_SERVER.getAddress().getPort() + "/jwks.json");
        registry.add("ltz.messaging.auth.issuer", () -> ISSUER);
        registry.add("ltz.messaging.auth.audience", () -> AUDIENCE);
    }

    /** Gerçek sunucudaki filtre sırası: gövde sınırı, ardından Spring Security zinciri. */
    @BeforeEach
    void setUpMockMvc() {
        Filter bodySizeLimit = context.getBean("requestBodySizeLimitFilter", FilterRegistrationBean.class).getFilter();
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(bodySizeLimit)
                .apply(springSecurity())
                .build();
        if (blocks != null) {
            blocks.reset();
        }
    }

    protected static UUID newUser() {
        return UUID.randomUUID();
    }

    // ------------------------------------------------------------ token

    /** Auth'un verdiği biçimde (RS256, typ at+jwt, iss, aud, sub, roles, nbf) geçerli bir access token. */
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
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).type(type).build();
            SignedJWT jwt = new SignedJWT(header, claims);
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException(exception);
        }
    }

    protected static RSAKey generateKey(String keyId) {
        try {
            return new RSAKeyGenerator(2048).keyID(keyId).generate();
        } catch (JOSEException exception) {
            throw new IllegalStateException(exception);
        }
    }

    // ------------------------------------------------------------ API çağrıları

    protected ResultActions openDirect(UUID actor, UUID other) throws Exception {
        return mockMvc.perform(post(MessagingApiPaths.DIRECT_CONVERSATIONS)
                .header(HttpHeaders.AUTHORIZATION, bearer(actor))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"%s\"}".formatted(other)));
    }

    protected UUID openDirectAndGetId(UUID actor, UUID other) throws Exception {
        return idOf(openDirect(actor, other));
    }

    protected ResultActions createGroup(UUID owner, String title, UUID... memberIds) throws Exception {
        return mockMvc.perform(post(MessagingApiPaths.GROUP_CONVERSATIONS)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"%s\",\"memberIds\":%s}".formatted(title, jsonArray(memberIds))));
    }

    protected UUID createGroupAndGetId(UUID owner, UUID... memberIds) throws Exception {
        return idOf(createGroup(owner, "Takım", memberIds));
    }

    protected ResultActions getConversation(UUID viewer, UUID conversationId) throws Exception {
        return mockMvc.perform(get(MessagingApiPaths.CONVERSATION, conversationId)
                .header(HttpHeaders.AUTHORIZATION, bearer(viewer)));
    }

    protected ResultActions addMembers(UUID actor, UUID conversationId, UUID... userIds) throws Exception {
        return mockMvc.perform(post(MessagingApiPaths.MEMBERS, conversationId)
                .header(HttpHeaders.AUTHORIZATION, bearer(actor))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userIds\":%s}".formatted(jsonArray(userIds))));
    }

    protected ResultActions removeMember(UUID actor, UUID conversationId, UUID target) throws Exception {
        return mockMvc.perform(delete(MessagingApiPaths.MEMBER, conversationId, target)
                .header(HttpHeaders.AUTHORIZATION, bearer(actor)));
    }

    protected ResultActions changeRole(UUID actor, UUID conversationId, UUID target, String role) throws Exception {
        return mockMvc.perform(put(MessagingApiPaths.MEMBER_ROLE, conversationId, target)
                .header(HttpHeaders.AUTHORIZATION, bearer(actor))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"%s\"}".formatted(role)));
    }

    protected ResultActions leave(UUID actor, UUID conversationId) throws Exception {
        return mockMvc.perform(post(MessagingApiPaths.LEAVE, conversationId)
                .header(HttpHeaders.AUTHORIZATION, bearer(actor)));
    }

    protected ResultActions sendMessage(UUID sender, UUID conversationId, String clientMessageId, String body)
            throws Exception {
        return mockMvc.perform(post(MessagingApiPaths.MESSAGES, conversationId)
                .header(HttpHeaders.AUTHORIZATION, bearer(sender))
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonPath.parse(Map.of("clientMessageId", clientMessageId, "body", body))
                        .jsonString()));
    }

    /** Yeni bir istemci kimliğiyle mesaj gönderir (201 beklenir) ve mesajın {@code seq} değerini döner. */
    protected long send(UUID sender, UUID conversationId, String body) throws Exception {
        String response = sendMessage(sender, conversationId, UUID.randomUUID().toString(), body)
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.seq")).longValue();
    }

    protected ResultActions history(UUID viewer, UUID conversationId, String query) throws Exception {
        return mockMvc.perform(get(MessagingApiPaths.MESSAGES + query, conversationId)
                .header(HttpHeaders.AUTHORIZATION, bearer(viewer)));
    }

    protected ResultActions listConversations(UUID user, String query) throws Exception {
        return mockMvc.perform(get(MessagingApiPaths.CONVERSATIONS + query)
                .header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    protected ResultActions markRead(UUID user, UUID conversationId, long seq) throws Exception {
        return mockMvc.perform(post(MessagingApiPaths.READ, conversationId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"seq\":%d}".formatted(seq)));
    }

    // ------------------------------------------------------------ DB gözlemi

    protected int conversationCount() {
        return jdbc.queryForObject("select count(*) from conversations", Integer.class);
    }

    /** Kullanıcının sohbetteki rolü; aktif üye değilse {@code null}. */
    protected String activeRole(UUID conversationId, UUID userId) {
        List<String> roles = jdbc.queryForList("select role from conversation_members "
                + "where conversation_id = ? and user_id = ? and left_at is null", String.class, conversationId, userId);
        return roles.isEmpty() ? null : roles.getFirst();
    }

    protected int activeMemberCount(UUID conversationId) {
        return jdbc.queryForObject("select count(*) from conversation_members "
                + "where conversation_id = ? and left_at is null", Integer.class, conversationId);
    }

    protected static UUID idOf(ResultActions result) throws Exception {
        return UUID.fromString(JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id"));
    }

    protected static String jsonArray(UUID... ids) {
        return Arrays.stream(ids).map(id -> "\"" + id + "\"").collect(Collectors.joining(",", "[", "]"));
    }

    private static HttpServer startJwksServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/jwks.json", exchange -> {
                byte[] jwks = new JWKSet(SIGNING_KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
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

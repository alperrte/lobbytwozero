package com.ltz.game.infrastructure.security;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
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
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sentetik token sınırları; gerçek Auth üreticisi ayrı test sınıfında çalıştırılır. */
class AuthJwtContractTest {
    static final UUID SUBJECT = UUID.randomUUID();
    static final KeyPair KEYS = keys();
    static final KeyPair OTHER_KEYS = keys();
    static final String KID = "contract-key";
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-29T12:00:00Z"), ZoneOffset.UTC);
    HttpServer server;
    final AtomicInteger requests = new AtomicInteger();
    final AtomicReference<String> jwks = new AtomicReference<>(publicKeys(KEYS, KID));
    final AtomicInteger httpStatus = new AtomicInteger(200);

    @BeforeEach void serveKeys() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/auth/.well-known/jwks.json", exchange -> {
            requests.incrementAndGet();
            byte[] body = jwks.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(httpStatus.get(), body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
            exchange.close();
        });
        server.start();
    }

    @AfterEach void stop() { server.stop(0); }

    JwtDecoder decoder() {
        return new JwtDecoderConfiguration().jwtDecoder(properties(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1/auth/.well-known/jwks.json", null));
    }

    static JwtSecurityProperties properties(String uri, String pem) {
        return new JwtSecurityProperties("ltz-auth", "ltz-api", uri, pem, "RS256", "sub", "roles", Duration.ofSeconds(30));
    }

    static KeyPair keys() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(3072);
            return generator.generateKeyPair();
        } catch (Exception error) { throw new IllegalStateException(error); }
    }

    static String publicKeys(KeyPair pair, String id) {
        return new JWKSet(new RSAKey.Builder((RSAPublicKey) pair.getPublic()).keyID(id)
                .algorithm(JWSAlgorithm.RS256).build()).toString();
    }

    static JWTClaimsSet.Builder claims() {
        return claims(Instant.now());
    }

    static JWTClaimsSet.Builder claims(Instant now) {
        return new JWTClaimsSet.Builder().issuer("ltz-auth").audience("ltz-api").subject(SUBJECT.toString())
                .issueTime(Date.from(now)).notBeforeTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(60)))
                .jwtID(UUID.randomUUID().toString()).claim("sid", UUID.randomUUID().toString())
                .claim("ver", 0L).claim("amr", List.of("pwd")).claim("roles", List.of("PLAYER"));
    }

    static JWSHeader header(JWSAlgorithm algorithm, String type, String kid) {
        var builder = new JWSHeader.Builder(algorithm).keyID(kid);
        if (type != null) builder.type(new JOSEObjectType(type));
        return builder.build();
    }

    static String sign(JWTClaimsSet.Builder claims, JWSHeader header, KeyPair pair) {
        try {
            SignedJWT jwt = new SignedJWT(header, claims.build());
            jwt.sign(JWSAlgorithm.HS256.equals(header.getAlgorithm())
                    ? new MACSigner(new byte[32]) : new RSASSASigner(pair.getPrivate()));
            return jwt.serialize();
        } catch (Exception error) { throw new IllegalStateException(error); }
    }

    static String sign(JWTClaimsSet.Builder claims) { return sign(claims, header(JWSAlgorithm.RS256, "at+jwt", KID), KEYS); }

    @Test void acceptsAuthShapeAndCachesPublicKeys() {
        JwtDecoder decoder = decoder();
        String token = sign(claims());
        assertEquals(SUBJECT.toString(), decoder.decode(token).getSubject());
        int loaded = requests.get();
        assertTrue(loaded > 0);
        decoder.decode(token);
        decoder.decode(token);
        assertEquals(loaded, requests.get(), "Bilinen anahtar her istekte tekrar indirilmemeli");
    }

    static Stream<String> invalidTokens() {
        var wrongType = header(JWSAlgorithm.RS256, "JWT", KID);
        return Stream.of("broken", "a.b.c",
                sign(claims().issuer("wrong")), sign(claims().issuer(null)),
                sign(claims().audience("wrong")), sign(claims().audience((List<String>) null)),
                sign(claims().subject("not-a-uuid")), sign(claims().subject("1-1-1-1-1")), sign(claims().subject(null)),
                sign(claims().expirationTime(null)), sign(claims().issueTime(null)), sign(claims().notBeforeTime(null)),
                sign(claims().issueTime(Date.from(Instant.now().minusSeconds(1200)))
                        .notBeforeTime(Date.from(Instant.now().minusSeconds(1200))).expirationTime(Date.from(Instant.now().minusSeconds(600)))),
                sign(claims().notBeforeTime(Date.from(Instant.now().plusSeconds(600)))),
                sign(claims().issueTime(Date.from(Instant.now().plusSeconds(120)))),
                sign(claims(), wrongType, KEYS),
                sign(claims(), header(JWSAlgorithm.RS256, null, KID), KEYS),
                sign(claims(), header(JWSAlgorithm.RS256, "svc+jwt", KID), KEYS),
                sign(claims(), header(JWSAlgorithm.RS256, "at+jwt", null), KEYS),
                sign(claims(), header(JWSAlgorithm.RS256, "at+jwt", "unknown"), KEYS),
                sign(claims(), header(JWSAlgorithm.RS256, "at+jwt", KID), OTHER_KEYS),
                sign(claims(), header(JWSAlgorithm.RS384, "at+jwt", KID), KEYS),
                sign(claims(), header(JWSAlgorithm.RS512, "at+jwt", KID), KEYS),
                sign(claims(), header(JWSAlgorithm.HS256, "at+jwt", KID), KEYS),
                new com.nimbusds.jwt.PlainJWT(claims().build()).serialize());
    }

    @ParameterizedTest(name = "invalid token #{index}") @MethodSource("invalidTokens")
    void rejectsInvalidTokens(String token) {
        JwtDecoder decoder = decoder();
        assertThrows(JwtException.class, () -> decoder.decode(token));
    }

    @Test void unknownKidRefreshesKeysAndStillRequiresValidSignature() {
        JwtDecoder decoder = decoder();
        decoder.decode(sign(claims()));
        int loaded = requests.get();
        jwks.set(publicKeys(OTHER_KEYS, "rotated"));
        String rotated = sign(claims(), header(JWSAlgorithm.RS256, "at+jwt", "rotated"), OTHER_KEYS);
        assertEquals(SUBJECT.toString(), decoder.decode(rotated).getSubject());
        assertTrue(requests.get() > loaded);
        String forged = sign(claims(), header(JWSAlgorithm.RS256, "at+jwt", "rotated"), KEYS);
        assertThrows(JwtException.class, () -> decoder.decode(forged));
    }

    @Test void unavailableOrMalformedJwksCannotAuthenticate() {
        httpStatus.set(503);
        JwtDecoder unavailable = decoder();
        String token = sign(claims());
        assertThrows(JwtException.class, () -> unavailable.decode(token));
        httpStatus.set(200);
        jwks.set("{}");
        JwtDecoder malformed = decoder();
        assertThrows(JwtException.class, () -> malformed.decode(token));
    }

    @Test void explicitPublicKeyTestOverrideAndFailClosedRemainAvailable() {
        String pem = "-----BEGIN PUBLIC KEY-----\n" + Base64.getEncoder().encodeToString(KEYS.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----";
        var configuration = new JwtDecoderConfiguration();
        assertEquals(SUBJECT.toString(), configuration.jwtDecoder(properties(null, pem)).decode(sign(claims())).getSubject());
        JwtDecoder closed = configuration.jwtDecoder(properties(null, null));
        String token = sign(claims());
        assertThrows(JwtException.class, () -> closed.decode(token));
    }

    @Test void configurationCannotChangeAuthContractOrDisableTimeValidation() {
        for (String algorithm : List.of("HS256", "RS384", "RS512"))
            rejected(new JwtSecurityProperties("iss", "aud", "http://localhost/keys", null, algorithm, "sub", "roles", Duration.ZERO));
        for (Duration skew : List.of(Duration.ofSeconds(-1), Duration.ofSeconds(31), Duration.ofSeconds(61)))
            rejected(new JwtSecurityProperties("iss", "aud", "http://localhost/keys", null, "RS256", "sub", "roles", skew));
        rejected(new JwtSecurityProperties("iss", "aud", "http://localhost/keys", null, "RS256", "uid", "roles", Duration.ZERO));
        rejected(new JwtSecurityProperties("iss", "aud", "http://localhost/keys", null, "RS256", "sub", "permissions", Duration.ZERO));
        for (String uri : List.of("ftp://localhost/keys", "http://user:pass@localhost/keys", "http://localhost/keys?token=x", "http://localhost/keys#key"))
            rejected(properties(uri, null));
    }

    @Test void defaultClockSkewBindsToThirtySeconds() {
        var source = new MapConfigurationPropertySource(Map.of(
                "ltz.security.jwt.issuer", "ltz-auth", "ltz.security.jwt.audience", "ltz-api",
                "ltz.security.jwt.jwk-set-uri", "http://127.0.0.1/keys"));
        var bound = new Binder(source).bind("ltz.security.jwt", Bindable.of(JwtSecurityProperties.class)).get();
        assertEquals(Duration.ofSeconds(30), bound.clockSkew());
    }

    @ParameterizedTest @ValueSource(ints = {0, 30})
    void explicitClockSkewBindingPreservesZeroAndThirtySeconds(int seconds) {
        var source = new MapConfigurationPropertySource(Map.of(
                "ltz.security.jwt.issuer", "ltz-auth", "ltz.security.jwt.audience", "ltz-api",
                "ltz.security.jwt.jwk-set-uri", "http://127.0.0.1/keys",
                "ltz.security.jwt.clock-skew", seconds + "s"));
        var bound = new Binder(source).bind("ltz.security.jwt", Bindable.of(JwtSecurityProperties.class)).get();
        assertEquals(Duration.ofSeconds(seconds), bound.clockSkew());
    }

    @Test void nullConstructorClockSkewUsesThirtySecondDefault() {
        var properties = new JwtSecurityProperties("ltz-auth", "ltz-api", "http://127.0.0.1/keys", null,
                "RS256", "sub", "roles", null);
        assertEquals(Duration.ofSeconds(30), properties.clockSkew());
        JwtDecoder decoder = timedDecoder(null);
        assertEquals(SUBJECT.toString(), decoder.decode(sign(timedClaims("iat", 30))).getSubject());
        assertThrows(JwtException.class, () -> decoder.decode(sign(timedClaims("iat", 31))));
    }

    @ParameterizedTest @ValueSource(ints = {0, 30})
    void zeroAndThirtySecondConfigurationsAcceptValidTokens(int seconds) {
        assertEquals(SUBJECT.toString(), timedDecoder(Duration.ofSeconds(seconds)).decode(sign(claims(CLOCK.instant()))).getSubject());
    }

    @ParameterizedTest @CsvSource({
            "exp,-29,true", "exp,-30,true", "exp,-31,false",
            "nbf,29,true", "nbf,30,true", "nbf,31,false",
            "iat,29,true", "iat,30,true", "iat,31,false"
    })
    void timestampBoundariesUseTheSameThirtySecondSkew(String claim, int offset, boolean accepted) {
        String token = sign(timedClaims(claim, offset));
        JwtDecoder decoder = timedDecoder(Duration.ofSeconds(30));
        if (accepted) assertEquals(SUBJECT.toString(), decoder.decode(token).getSubject());
        else assertThrows(JwtException.class, () -> decoder.decode(token));
    }

    @ParameterizedTest @CsvSource({"exp,-1", "nbf,1", "iat,1"})
    void zeroSkewDoesNotGrantTimestampGrace(String claim, int offset) {
        String token = sign(timedClaims(claim, offset));
        JwtDecoder decoder = timedDecoder(Duration.ZERO);
        assertThrows(JwtException.class, () -> decoder.decode(token));
    }

    private static JWTClaimsSet.Builder timedClaims(String claim, int offset) {
        Instant now = CLOCK.instant();
        return switch (claim) {
            case "exp" -> claims(now.plusSeconds(offset - 60L));
            case "nbf" -> claims(now).notBeforeTime(Date.from(now.plusSeconds(offset)));
            case "iat" -> claims(now).issueTime(Date.from(now.plusSeconds(offset)));
            default -> throw new IllegalArgumentException("Unknown timestamp claim");
        };
    }

    private JwtDecoder timedDecoder(Duration skew) {
        String uri = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1/auth/.well-known/jwks.json";
        var props = new JwtSecurityProperties("ltz-auth", "ltz-api", uri, null, "RS256", "sub", "roles", skew);
        var decoder = (NimbusJwtDecoder) new JwtDecoderConfiguration().jwtDecoder(props);
        decoder.setJwtValidator(JwtDecoderConfiguration.validator(props, CLOCK));
        return decoder;
    }

    private static void rejected(JwtSecurityProperties properties) {
        var configuration = new JwtDecoderConfiguration();
        assertThrows(IllegalStateException.class, () -> configuration.jwtDecoder(properties));
    }
}

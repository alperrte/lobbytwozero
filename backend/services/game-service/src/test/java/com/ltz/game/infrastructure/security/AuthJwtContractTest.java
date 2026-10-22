package com.ltz.game.infrastructure.security;

import com.nimbusds.jose.*;
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
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import static org.junit.jupiter.api.Assertions.*;

/** Sentetik token sınırları; gerçek Auth üreticisi ayrı test sınıfında çalıştırılır. */
class AuthJwtContractTest {
    static final UUID SUBJECT = UUID.randomUUID();
    static final KeyPair KEYS = keys();
    static final KeyPair OTHER_KEYS = keys();
    static final String KID = "contract-key";
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
        return new JwtSecurityProperties("ltz-auth", "ltz-api", uri, pem, "RS256", "sub", "roles", Duration.ofSeconds(60));
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
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder().issuer("ltz-auth").audience("ltz-api").subject(SUBJECT.toString())
                .issueTime(Date.from(now)).notBeforeTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(300)))
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
        for (Duration skew : List.of(Duration.ofSeconds(-1), Duration.ofSeconds(61)))
            rejected(new JwtSecurityProperties("iss", "aud", "http://localhost/keys", null, "RS256", "sub", "roles", skew));
        rejected(new JwtSecurityProperties("iss", "aud", "http://localhost/keys", null, "RS256", "uid", "roles", Duration.ZERO));
        rejected(new JwtSecurityProperties("iss", "aud", "http://localhost/keys", null, "RS256", "sub", "permissions", Duration.ZERO));
        for (String uri : List.of("ftp://localhost/keys", "http://user:pass@localhost/keys", "http://localhost/keys?token=x", "http://localhost/keys#key"))
            rejected(properties(uri, null));
    }

    private static void rejected(JwtSecurityProperties properties) {
        var configuration = new JwtDecoderConfiguration();
        assertThrows(IllegalStateException.class, () -> configuration.jwtDecoder(properties));
    }
}

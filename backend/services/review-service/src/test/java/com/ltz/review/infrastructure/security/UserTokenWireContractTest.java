package com.ltz.review.infrastructure.security;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Review kullanıcı (at+jwt) doğrulayıcısı, gerçek RS256 imzaları ve gerçek HTTP JWKS ile. Token şekli Auth AccessTokenIssuer
 * çıktısıyla aynıdır (typ=at+jwt, kid, iss=ltz-auth, aud ltz-api, sub UUID, iat/nbf/exp, jti, sid, ver, roles, amr).
 * Yerel JWKS sunucusu kullanılır; canlı Auth servisi doğrulaması değildir.
 */
class UserTokenWireContractTest {
    private static final String ISSUER = "ltz-auth";
    private static final String AUDIENCE = "ltz-api";
    private static KeyPair current;
    private static KeyPair previous;
    private static KeyPair foreign;
    private static HttpServer jwks;
    private static UserTokenDecoderConfiguration configuration;
    private static JwtDecoder decoder;

    @BeforeAll static void start() throws Exception {
        current = rsa();
        previous = rsa();
        foreign = rsa();
        // Anahtar rotasyonu örtüşmesi: Auth JWKS'i geçerli + ek açık anahtarları birlikte yayınlar.
        byte[] body = new JWKSet(List.of(jwk(current, "current-key"), jwk(previous, "previous-key"))).toString().getBytes(StandardCharsets.UTF_8);
        jwks = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        jwks.createContext("/jwks", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
            exchange.close();
        });
        jwks.start();
        configuration = new UserTokenDecoderConfiguration();
        decoder = configuration.userJwtDecoder("http://127.0.0.1:" + jwks.getAddress().getPort() + "/jwks", ISSUER, AUDIENCE);
    }

    @AfterAll static void stop() {
        jwks.stop(0);
        configuration.closeClient();
    }

    @Test void authShapedTokenIsAcceptedWithSubjectAudienceAndCanonicalAuthorities() throws Exception {
        UUID account = UUID.randomUUID();
        Jwt jwt = decoder.decode(sign(current, "current-key", "at+jwt", claims(account)));
        assertEquals(account.toString(), jwt.getSubject());
        assertEquals(List.of(AUDIENCE), jwt.getAudience());
        assertEquals(List.of("ROLE_PLAYER"), UserTokenDecoderConfiguration.authorities(jwt).stream().map(Object::toString).toList());
    }

    @Test void rotationOverlapAcceptsCurrentAndPreviousKeyButNotAForeignKey() throws Exception {
        decoder.decode(sign(previous, "previous-key", "at+jwt", claims(UUID.randomUUID())));
        decoder.decode(sign(current, "current-key", "at+jwt", claims(UUID.randomUUID())));
        assertRejected(sign(foreign, "foreign-key", "at+jwt", claims(UUID.randomUUID())));
        assertRejected(sign(foreign, "current-key", "at+jwt", claims(UUID.randomUUID()))); // bilinen kid, yabancı anahtar
    }

    @Test void tamperedSignatureAndPayloadAreRejected() throws Exception {
        String token = sign(current, "current-key", "at+jwt", claims(UUID.randomUUID()));
        String[] parts = token.split("\\.");
        assertRejected(parts[0] + "." + parts[1] + "." + (parts[2].charAt(0) == 'A' ? 'B' : 'A') + parts[2].substring(1));
        String swappedPayload = sign(current, "current-key", "at+jwt", claims(UUID.randomUUID())).split("\\.")[1];
        assertRejected(parts[0] + "." + swappedPayload + "." + parts[2]);
    }

    @Test void algorithmConfusionAndUnsignedTokensAreRejected() throws Exception {
        assertRejected(new PlainJWT(claims(UUID.randomUUID())).serialize());
        var hs = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).type(new JOSEObjectType("at+jwt")).keyID("current-key").build(),
                claims(UUID.randomUUID()));
        hs.sign(new MACSigner(java.util.Arrays.copyOf(current.getPublic().getEncoded(), 64)));
        assertRejected(hs.serialize());
    }

    @Test void wrongTypeMissingKeyIdAndUnknownKeyIdAreRejected() throws Exception {
        assertRejected(sign(current, "current-key", "svc+jwt", claims(UUID.randomUUID())));
        assertRejected(sign(current, null, "at+jwt", claims(UUID.randomUUID())));
        assertRejected(sign(current, "unknown-key", "at+jwt", claims(UUID.randomUUID())));
    }

    @Test void expiredNotYetValidAndWrongIssuerOrAudienceAreRejected() throws Exception {
        Instant now = Instant.now();
        assertRejected(sign(current, "current-key", "at+jwt", base(UUID.randomUUID(), now.minusSeconds(600), now.minusSeconds(600), now.minusSeconds(120)).build()));
        assertRejected(sign(current, "current-key", "at+jwt", base(UUID.randomUUID(), now, now.plusSeconds(300), now.plusSeconds(600)).build()));
        assertRejected(sign(current, "current-key", "at+jwt", new JWTClaimsSet.Builder(claims(UUID.randomUUID())).issuer("attacker").build()));
        assertRejected(sign(current, "current-key", "at+jwt", new JWTClaimsSet.Builder(claims(UUID.randomUUID())).audience("review-service").build()));
        assertRejected(sign(current, "current-key", "at+jwt", new JWTClaimsSet.Builder(claims(UUID.randomUUID())).audience(List.of()).build()));
    }

    @Test void lifetimeAboveAuthMaximumIsRejectedAndTheMaximumIsAccepted() throws Exception {
        Instant now = Instant.now();
        decoder.decode(sign(current, "current-key", "at+jwt", base(UUID.randomUUID(), now, now, now.plusSeconds(900)).build()));
        assertRejected(sign(current, "current-key", "at+jwt", base(UUID.randomUUID(), now, now, now.plusSeconds(901)).build()));
    }

    @Test void nonCanonicalOrMissingSubjectAndMissingTimesAreRejected() throws Exception {
        assertRejected(sign(current, "current-key", "at+jwt", new JWTClaimsSet.Builder(claims(UUID.randomUUID())).subject("not-a-uuid").build()));
        assertRejected(sign(current, "current-key", "at+jwt", new JWTClaimsSet.Builder(claims(UUID.randomUUID())).subject(null).build()));
        assertRejected(sign(current, "current-key", "at+jwt", new JWTClaimsSet.Builder(claims(UUID.randomUUID())).notBeforeTime(null).build()));
        assertRejected(sign(current, "current-key", "at+jwt", new JWTClaimsSet.Builder(claims(UUID.randomUUID())).issueTime(null).build()));
        assertRejected(sign(current, "current-key", "at+jwt", new JWTClaimsSet.Builder(claims(UUID.randomUUID())).expirationTime(null).build()));
    }

    @Test void onlyCanonicalAuthRolesGrantAuthorities() throws Exception {
        for (Object roles : List.of(List.of("ROOT"), List.of(1, 2), "PLAYER", List.of("ROLE_PLAYER"), List.of("player"))) {
            Jwt jwt = decoder.decode(sign(current, "current-key", "at+jwt", new JWTClaimsSet.Builder(claims(UUID.randomUUID())).claim("roles", roles).build()));
            assertEquals(0, UserTokenDecoderConfiguration.authorities(jwt).size(), String.valueOf(roles));
        }
        Jwt mixed = decoder.decode(sign(current, "current-key", "at+jwt",
                new JWTClaimsSet.Builder(claims(UUID.randomUUID())).claim("roles", List.of("MODERATOR", "ROOT", "ADMIN")).build()));
        assertEquals(List.of("ROLE_MODERATOR", "ROLE_ADMIN"), UserTokenDecoderConfiguration.authorities(mixed).stream().map(Object::toString).toList());
        Jwt none = decoder.decode(sign(current, "current-key", "at+jwt",
                new JWTClaimsSet.Builder(claims(UUID.randomUUID())).claim("roles", null).build()));
        assertEquals(0, UserTokenDecoderConfiguration.authorities(none).size());
    }

    private static void assertRejected(String token) { assertThrows(JwtException.class, () -> decoder.decode(token)); }

    private static JWTClaimsSet claims(UUID account) {
        Instant now = Instant.now();
        return base(account, now, now, now.plusSeconds(300)).build();
    }

    private static JWTClaimsSet.Builder base(UUID account, Instant issuedAt, Instant notBefore, Instant expires) {
        return new JWTClaimsSet.Builder().issuer(ISSUER).subject(account.toString()).audience(AUDIENCE)
                .issueTime(Date.from(issuedAt)).notBeforeTime(Date.from(notBefore)).expirationTime(Date.from(expires))
                .jwtID(UUID.randomUUID().toString()).claim("sid", UUID.randomUUID().toString()).claim("ver", 0L)
                .claim("roles", List.of("PLAYER")).claim("amr", List.of("pwd"));
    }

    private static String sign(KeyPair keys, String keyId, String type, JWTClaimsSet claims) throws Exception {
        var header = new JWSHeader.Builder(JWSAlgorithm.RS256).type(new JOSEObjectType(type));
        if (keyId != null) header.keyID(keyId);
        var jwt = new SignedJWT(header.build(), claims);
        jwt.sign(new RSASSASigner(keys.getPrivate()));
        return jwt.serialize();
    }

    private static RSAKey jwk(KeyPair keys, String keyId) { return new RSAKey.Builder((RSAPublicKey) keys.getPublic()).keyID(keyId).build(); }

    private static KeyPair rsa() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }
}

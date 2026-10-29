package com.ltz.game.infrastructure.security;

import com.ltz.game.PostgresTestSupport;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * İmza kaynağı test kapsamında açıkça kaldırıldığında hiçbir token kabul edilmez.
 */
@SpringBootTest(properties = {"ltz.security.jwt.jwk-set-uri=", "ltz.security.jwt.public-key="})
class FailClosedSecurityTest extends PostgresTestSupport {
    @Autowired WebApplicationContext context;
    @Autowired JwtDecoder decoder;

    @Test void everyTokenIsRejectedWhenNoSignatureSourceIsConfigured() throws Exception {
        var keys = KeyPairGenerator.getInstance("RSA");
        keys.initialize(2048);
        var pair = keys.generateKeyPair();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), new JWTClaimsSet.Builder().subject(UUID.randomUUID().toString())
                .issuer("any").audience("any").claim("roles", List.of("ADMIN")).expirationTime(new Date(System.currentTimeMillis() + 600_000)).build());
        jwt.sign(new RSASSASigner(pair.getPrivate()));
        String serialized = jwt.serialize();
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(get("/api/v1/game/admin/games/" + UUID.randomUUID()).header("Authorization", "Bearer " + serialized))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/game/developer/games").header("Authorization", "Bearer " + serialized)).andExpect(status().isUnauthorized());
        assertThrows(org.springframework.security.oauth2.jwt.JwtException.class, () -> decoder.decode(serialized));
        // Herkese açık katalog etkilenmez.
        mvc.perform(get("/api/v1/game/games")).andExpect(status().isOk());
    }

    @Test void misconfiguredSignatureSourceFailsFastInsteadOfFailingOpen() throws Exception {
        var keys = KeyPairGenerator.getInstance("RSA");
        keys.initialize(2048);
        String pem = "-----BEGIN PUBLIC KEY-----\n" + Base64.getMimeEncoder().encodeToString(keys.generateKeyPair().getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----";
        String jwks = "https://auth.example/jwks";
        // Hem JWKS hem açık anahtar; issuer veya audience eksik; izin verilmeyen algoritma; bozuk anahtar; geçersiz JWKS adresi
        assertRejected(properties("iss", "aud", jwks, pem, "RS256"));
        assertRejected(properties(null, "aud", null, pem, "RS256"));
        assertRejected(properties("iss", " ", null, pem, "RS256"));
        assertRejected(properties("iss", "aud", null, pem, "HS256"));
        assertRejected(properties("iss", "aud", null, pem, "RS384"));
        assertRejected(properties("iss", "aud", null, pem, "RS512"));
        assertRejected(properties("iss", "aud", null, "not a key", "RS256"));
        assertRejected(properties("iss", "aud", "ftp://auth.example/jwks", null, "RS256"));
        // Geçerli yapılandırmalar decoder üretir
        assertNotNull(new JwtDecoderConfiguration().jwtDecoder(properties("iss", "aud", null, pem, "RS256")));
        assertNotNull(new JwtDecoderConfiguration().jwtDecoder(properties("iss", "aud", jwks, null, "RS256")));
    }

    private static JwtSecurityProperties properties(String issuer, String audience, String jwks, String key, String algorithm) {
        return new JwtSecurityProperties(issuer, audience, jwks, key, algorithm, "sub", "roles", Duration.ofSeconds(30));
    }

    private static void assertRejected(JwtSecurityProperties properties) {
        var configuration = new JwtDecoderConfiguration();
        assertThrows(IllegalStateException.class, () -> configuration.jwtDecoder(properties));
    }
}

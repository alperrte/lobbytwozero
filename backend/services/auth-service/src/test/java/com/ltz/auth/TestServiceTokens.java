package com.ltz.auth;

import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.ltz.auth.infrastructure.config.JwtProperties;
import com.ltz.auth.infrastructure.security.JwtSigningKey;
import com.ltz.auth.infrastructure.security.ServiceTokenIssuer;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

/** Yalnız test: Spring bağlamı olmadan servis token'ı üretmek için çalıştırma başına üretilen RSA 3072 anahtarı. */
public final class TestServiceTokens {

    public static final String ISSUER = "ltz-auth";
    private static final int RSA_BITS = 3072;
    private static final String PRIVATE_KEY = generateKey();
    private static final JwtSigningKey SIGNING_KEY = JwtSigningKey.fromBase64Pkcs8(PRIVATE_KEY);

    private TestServiceTokens() {
    }

    public static JwtSigningKey signingKey() {
        return SIGNING_KEY;
    }

    public static ServiceTokenIssuer issuer(Clock clock) {
        JwtProperties properties = new JwtProperties(ISSUER, "ltz-api", Duration.ofMinutes(5), PRIVATE_KEY, null);
        return new ServiceTokenIssuer(new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(SIGNING_KEY.jwk()))),
                SIGNING_KEY, properties, clock);
    }

    private static String generateKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(RSA_BITS);
            return Base64.getEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}

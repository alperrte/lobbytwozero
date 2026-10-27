package com.ltz.game.infrastructure.security;

import java.security.KeyPairGenerator;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import static org.junit.jupiter.api.Assertions.*;

class ServiceTokenDecoderConfigurationTest {
    static final Instant NOW = Instant.parse("2026-10-24T12:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    final ServiceTokenDecoderConfiguration config = new ServiceTokenDecoderConfiguration();

    @Test void missingKeysFailClosedAndInvalidConfigurationFailsFast() {
        var closed = config.serviceJwtDecoder("ltz-auth", "", "", "game-service");
        assertThrows(JwtException.class, () -> closed.decode("token"));
        assertThrows(IllegalStateException.class, () -> config.serviceJwtDecoder("", "", "", "game-service"));
        assertThrows(IllegalStateException.class, () -> config.serviceJwtDecoder("ltz-auth", "", "", "social-service"));
        assertThrows(IllegalStateException.class, () -> config.serviceJwtDecoder("ltz-auth", "http://auth/jwks", "key", "game-service"));
        assertThrows(IllegalStateException.class, () -> config.serviceJwtDecoder("ltz-auth", "", "invalid", "game-service"));
        for (String uri : List.of("ftp://auth/jwks", "http://user@auth/jwks", "http://auth/jwks?q=x", "http://auth/jwks#x", "/jwks"))
            assertThrows(IllegalStateException.class, () -> config.serviceJwtDecoder("ltz-auth", uri, "", "game-service"));
        assertNotNull(config.serviceJwtDecoder("ltz-auth", "https://auth.example.test/jwks", "", "game-service"));
    }

    @Test void publicKeyModeUsesRealSignatureValidation() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var keys = generator.generateKeyPair();
        String pem = "-----BEGIN PUBLIC KEY-----\n" + Base64.getEncoder().encodeToString(keys.getPublic().getEncoded()) + "\n-----END PUBLIC KEY-----";
        var decoder = config.serviceJwtDecoder("ltz-auth", "", pem, "game-service");
        assertThrows(JwtException.class, () -> decoder.decode("invalid"));
    }

    @Test void strictContractChecksRequiredHeadersClaimsAndLifetimeWithFixedClock() {
        var validator = ServiceTokenDecoderConfiguration.validator("ltz-auth", "game-service", CLOCK);
        assertFalse(validator.validate(jwt().build()).hasErrors());
        for (Jwt.Builder invalid : List.of(
                jwt().header("typ", "at+jwt"), jwt().header("typ", "SVC+JWT"), jwt().header("kid", ""),
                jwt().audience(List.of("game-service", "other-service")), jwt().subject("user"), jwt().subject(""),
                jwt().jti(""), jwt().claims(claims -> claims.remove("jti")),
                jwt().claims(claims -> claims.remove("nbf")),
                jwt().issuedAt(NOW.plusSeconds(31)).expiresAt(NOW.plusSeconds(60)),
                jwt().expiresAt(NOW.plusSeconds(61)),
                jwt().issuedAt(NOW.minusSeconds(120)).expiresAt(NOW.minusSeconds(60)),
                jwt().issuedAt(NOW.plusSeconds(60)).expiresAt(NOW.plusSeconds(91)),
                jwt().issuer("another-issuer"))) {
            assertTrue(validator.validate(invalid.build()).hasErrors());
        }
    }

    static Jwt.Builder jwt() {
        return Jwt.withTokenValue("synthetic-validator-input").header("alg", "RS256").header("typ", "svc+jwt").header("kid", "key")
                .issuer("ltz-auth").audience(List.of("game-service")).subject("svc:auth-service").jti("test-request")
                .issuedAt(NOW).notBefore(NOW).expiresAt(NOW.plusSeconds(30));
    }
}

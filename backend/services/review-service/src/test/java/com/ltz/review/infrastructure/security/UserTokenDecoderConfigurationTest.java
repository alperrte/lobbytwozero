package com.ltz.review.infrastructure.security;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserTokenDecoderConfigurationTest {
    static final Instant NOW = Instant.parse("2026-10-31T10:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test void tokenLifetimeUpperBoundAndFutureToleranceAreEnforced() {
        var validator = UserTokenDecoderConfiguration.contract("ltz-api", CLOCK);
        assertFalse(validator.validate(jwt().build()).hasErrors());
        assertFalse(validator.validate(jwt().issuedAt(NOW.plusSeconds(30)).expiresAt(NOW.plusSeconds(930)).build()).hasErrors());
        for (var value : List.of(jwt().expiresAt(NOW.plusSeconds(901)), jwt().issuedAt(NOW.plusSeconds(31)),
                jwt().header("typ", "svc+jwt"), jwt().header("kid", ""), jwt().subject("1-1-1-1-1"),
                jwt().audience(List.of("review-service")), jwt().claims(claims -> claims.remove("sub")),
                jwt().claims(claims -> claims.remove("iat")), jwt().claims(claims -> claims.remove("exp")),
                jwt().claims(claims -> claims.remove("nbf")))) {
            assertTrue(validator.validate(value.build()).hasErrors());
        }
        assertTrue(UserTokenPolicy.ERASURE_BLOCK_TTL.compareTo(UserTokenPolicy.MAX_ACCESS_TTL.plus(UserTokenPolicy.CLOCK_SKEW.multipliedBy(2))) > 0);
    }

    @Test void missingKeysFailClosedAndInvalidConfigurationFailsFast() {
        var configuration = new UserTokenDecoderConfiguration();
        assertThrows(JwtException.class, () -> configuration.userJwtDecoder("", "ltz-auth", "ltz-api").decode("token"));
        for (String uri : List.of("/jwks", "ftp://auth/jwks", "http://user@auth/jwks", "http://auth/jwks?q=1", "http://auth/jwks#fragment"))
            assertThrows(IllegalStateException.class, () -> configuration.userJwtDecoder(uri, "ltz-auth", "ltz-api"));
        assertThrows(IllegalStateException.class, () -> configuration.userJwtDecoder("http://auth/jwks", "", "ltz-api"));
        assertThrows(IllegalStateException.class, () -> configuration.userJwtDecoder("http://auth/jwks", "ltz-auth", ""));
        configuration.closeClient();
        configuration.userJwtDecoder("http://127.0.0.1:1/jwks", "ltz-auth", "ltz-api");
        configuration.closeClient();
        configuration.closeClient();
    }

    static Jwt.Builder jwt() {
        return Jwt.withTokenValue("test").header("typ", "at+jwt").header("kid", "test-key").subject(UUID.randomUUID().toString())
                .issuedAt(NOW).notBefore(NOW).expiresAt(NOW.plusSeconds(900)).audience(List.of("ltz-api"));
    }
}

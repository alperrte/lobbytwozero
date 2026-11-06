package com.ltz.performance.infrastructure.security;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import static org.assertj.core.api.Assertions.assertThat;

class PerformanceDeviceJwtValidatorTest {
    private static final Instant ISSUED = Instant.parse("2026-11-06T10:00:00Z");

    @Test
    void revocationWindowCannotBeExtendedByUserClockSkew() {
        Jwt jwt = token(ISSUED.plusSeconds(60));
        assertThat(validate(jwt, ISSUED.plusSeconds(59))).isFalse();
        assertThat(validate(jwt, ISSUED.plusSeconds(60))).isTrue();
        assertThat(validate(jwt, ISSUED.plusSeconds(61))).isTrue();
    }

    @Test
    void issuerCannotExtendDeviceLifetimePastSixtySeconds() {
        assertThat(validate(token(ISSUED.plusSeconds(61)), ISSUED.plusSeconds(1))).isTrue();
    }

    private static boolean validate(Jwt jwt, Instant now) {
        return PerformanceDeviceJwtValidator.validator("ltz-auth", Clock.fixed(now, ZoneOffset.UTC))
                .validate(jwt).hasErrors();
    }

    private static Jwt token(Instant expires) {
        return Jwt.withTokenValue("validator-fixture").header("typ", "dev+jwt").header("kid", "fixture-key")
                .issuer("ltz-auth").subject(UUID.randomUUID().toString()).audience(List.of("performance-service"))
                .claim("account_id", UUID.randomUUID().toString()).claim("scope", "performance:sync:write")
                .jti(UUID.randomUUID().toString()).issuedAt(ISSUED).notBefore(ISSUED).expiresAt(expires).build();
    }
}

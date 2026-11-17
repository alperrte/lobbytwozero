package com.ltz.performance.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;

class PerformanceJwtValidatorsTest {
    private static final Instant NOW = Instant.parse("2026-11-12T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private Jwt.Builder token(boolean service) {
        return Jwt.withTokenValue("test-only").header("alg", "RS256").header("kid", "fixture-key")
                .header("typ", service ? "svc+jwt" : "at+jwt").issuer("ltz-auth")
                .audience(List.of("performance-service"))
                .subject(service ? "svc:hardware-service" : UUID.randomUUID().toString())
                .issuedAt(NOW).notBefore(NOW).expiresAt(NOW.plusSeconds(60)).jti("fixture-id");
    }

    private boolean rejected(Jwt jwt, boolean service) {
        return PerformanceJwtValidators.validator("ltz-auth", "performance-service", service, CLOCK).validate(jwt).hasErrors();
    }

    @Test
    void preservesSeparateUserAndServiceIdentityRules() {
        assertThat(rejected(token(false).build(), false)).isFalse();
        assertThat(rejected(token(true).build(), true)).isFalse();
        assertThat(rejected(token(true).subject(UUID.randomUUID().toString()).build(), true)).isTrue();
        assertThat(rejected(token(false).subject("svc:hardware-service").build(), false)).isTrue();
        assertThat(rejected(token(true).audience(List.of("performance-service", "other")).build(), true)).isTrue();
        assertThat(rejected(token(false).audience(List.of("performance-service", "other")).build(), false)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"kid", "aud", "iat", "exp", "nbf", "sub", "jti"})
    void serviceRejectsMissingIdentityAndTimeClaims(String claim) {
        var builder = token(true);
        if ("kid".equals(claim)) builder.headers(headers -> headers.remove(claim));
        else builder.claims(claims -> claims.remove(claim));
        assertThat(rejected(builder.build(), true)).isTrue();
    }

    @Test
    void lifetimeAndClockSkewRemainBounded() {
        assertThat(rejected(token(false).expiresAt(NOW.plusSeconds(61)).build(), false)).isTrue();
        assertThat(rejected(token(true).issuedAt(NOW.plusSeconds(31)).expiresAt(NOW.plusSeconds(91)).build(), true)).isTrue();
        assertThat(rejected(token(false).notBefore(NOW.plusSeconds(61)).build(), false)).isTrue();
        assertThat(rejected(token(false).issuedAt(NOW.minusSeconds(89)).notBefore(NOW.minusSeconds(89))
                .expiresAt(NOW.minusSeconds(29)).build(), false)).isFalse();
        assertThat(rejected(token(true).jti(" ").build(), true)).isTrue();
        assertThat(rejected(token(true).header("kid", " ").build(), true)).isTrue();
    }

    @Test
    void rawClaimsAreRequiredBeforeCoercionAndUuidsAreCanonical() {
        var claims = new HashMap<String, Object>();
        claims.put("exp", NOW.plusSeconds(60).getEpochSecond());
        claims.put("nbf", NOW.getEpochSecond());
        claims.put("iat", NOW.getEpochSecond());
        claims.put("sub", UUID.randomUUID().toString());
        claims.put("aud", List.of("performance-service"));
        claims.put("iss", "ltz-auth");
        assertThat(PerformanceJwtValidators.requiredRawClaims(claims)).containsKeys("exp", "nbf", "iat");
        claims.remove("sub");
        assertThatThrownBy(() -> PerformanceJwtValidators.requiredRawClaims(claims)).isInstanceOf(BadJwtException.class);
        assertThat(PerformanceJwtValidators.canonicalUuid("1-1-1-1-1")).isFalse();
        assertThat(PerformanceJwtValidators.canonicalUuid(null)).isFalse();
    }
}

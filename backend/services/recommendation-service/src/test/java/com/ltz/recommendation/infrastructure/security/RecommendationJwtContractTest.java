package com.ltz.recommendation.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;

class RecommendationJwtContractTest {
    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void timestampIssuerAudienceSubjectAndLifetimeAreEnforced() {
        var validator = RecommendationJwtValidators.validator("ltz-auth", "ltz-api", false, CLOCK);
        assertThat(validator.validate(user().build()).hasErrors()).isFalse();
        for (Jwt jwt : List.of(user().issuer("other").build(), user().audience(List.of("other")).build(),
                user().subject("not-uuid").build(), user().header("typ", "svc+jwt").build(),
                user().expiresAt(NOW.plusSeconds(61)).build(),
                user().issuedAt(NOW.minusSeconds(180)).expiresAt(NOW.minusSeconds(120)).notBefore(NOW.minusSeconds(180)).build(),
                user().notBefore(NOW.plusSeconds(45)).build(), user().header("kid", "").build())) {
            assertThat(validator.validate(jwt).hasErrors()).isTrue();
        }
    }

    @Test
    void serviceTokensHaveSingleAudienceShortLifetimeAndJti() {
        var validator = RecommendationJwtValidators.validator("ltz-auth", "recommendation-service", true, CLOCK);
        Jwt.Builder service = user().header("typ", "svc+jwt").audience(List.of("recommendation-service"))
                .subject("svc:auth-service").jti("test-jti");
        assertThat(validator.validate(service.build()).hasErrors()).isFalse();
        assertThat(validator.validate(service.audience(List.of("recommendation-service", "other")).build()).hasErrors()).isTrue();
        assertThat(validator.validate(service.audience(List.of("recommendation-service")).subject("user").build()).hasErrors()).isTrue();
    }

    @Test
    void rawClaimsCannotOmitRequiredTimesOrIdentity() {
        assertThatThrownBy(() -> RecommendationJwtValidators.requiredRawClaims(Map.of("sub", "user")))
                .isInstanceOf(BadJwtException.class);
        assertThat(RecommendationJwtValidators.canonicalUuid("bad")).isFalse();
        assertThat(RecommendationJwtValidators.canonicalUuid(null)).isFalse();
    }

    @Test
    void configuredAuthOriginAndTimeoutAreRestricted() {
        var valid = new RecommendationAuthProperties(URI.create("http://127.0.0.1:8080"), "ltz-auth", "ltz-api",
                Duration.ofSeconds(3), Duration.ofSeconds(5));
        assertThat(valid.jwksUri().getPath()).isEqualTo("/api/v1/auth/.well-known/jwks.json");
        for (String origin : List.of("file:///tmp/key", "https://example.test/path", "https://user@example.test", "https://example.test?q=x")) {
            assertThatThrownBy(() -> new RecommendationAuthProperties(URI.create(origin), "ltz-auth", "ltz-api",
                    Duration.ofSeconds(3), Duration.ofSeconds(5))).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new RecommendationAuthProperties(URI.create("https://example.test"), "", "ltz-api",
                Duration.ofSeconds(3), Duration.ofSeconds(5))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RecommendationAuthProperties(URI.create("https://example.test"), "ltz-auth", "ltz-api",
                Duration.ZERO, Duration.ofSeconds(5))).isInstanceOf(IllegalArgumentException.class);
    }

    private static Jwt.Builder user() {
        return Jwt.withTokenValue("validation-contract").header("typ", "at+jwt").header("kid", "test-key")
                .issuer("ltz-auth").audience(List.of("ltz-api")).subject("00000000-0000-0000-0000-000000000001")
                .issuedAt(NOW).notBefore(NOW).expiresAt(NOW.plusSeconds(60));
    }
}

package com.ltz.hardware.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.ltz.hardware.application.port.AuthenticatedHardwareDevice;
import com.ltz.hardware.application.port.HardwareSyncDeviceAccess;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.Jwt;

class HardwareDeviceJwtValidatorTest {
    private static final Instant NOW = Instant.parse("2026-10-29T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID DEVICE = UUID.randomUUID();

    private Jwt.Builder token() {
        return Jwt.withTokenValue("test-only").header("alg", "RS256").header("typ", "dev+jwt").header("kid", "test-key")
                .issuer("ltz-auth").subject(DEVICE.toString()).audience(List.of("hardware-service"))
                .issuedAt(NOW).notBefore(NOW).expiresAt(NOW.plusSeconds(60)).jti(UUID.randomUUID().toString())
                .claim("account_id", OWNER.toString()).claim("scope", HardwareSyncDeviceAccess.PERMISSION);
    }

    private boolean rejected(Jwt jwt) {
        return HardwareDeviceJwtValidator.validator("ltz-auth", CLOCK).validate(jwt).hasErrors();
    }

    @Test
    void createsOnlyDeviceIdentityAndNarrowAuthorityEvenIfRolesArePresent() {
        Jwt jwt = token().claim("roles", List.of("ADMIN")).claim("scope", "other " + HardwareSyncDeviceAccess.PERMISSION).build();
        assertThat(rejected(jwt)).isFalse();
        var auth = new HardwareDeviceJwtAuthenticationConverter().convert(jwt);
        assertThat(auth.getPrincipal()).isEqualTo(new AuthenticatedHardwareDevice(OWNER, DEVICE));
        assertThat(auth.getAuthorities()).extracting("authority").containsExactly(HardwareSyncDeviceAccess.PERMISSION);
        assertThat(auth.getCredentials()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"at+jwt", "svc+jwt", "DEV+JWT", "application/dev+jwt", "JWT"})
    void rejectsEveryOtherTokenType(String type) {
        assertThat(rejected(token().header("typ", type).build())).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"iss", "sub", "aud", "account_id", "scope", "iat", "exp", "nbf", "jti", "kid", "typ"})
    void requiresEveryIdentityHeaderAndClaim(String name) {
        var builder = token();
        if (List.of("kid", "typ").contains(name)) builder.headers(headers -> headers.remove(name));
        else builder.claims(claims -> claims.remove(name));
        assertThat(rejected(builder.build())).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"owner", "subject", "scope", "scopeList", "ownerList", "blankKid", "blankJti", "audience", "issuer",
            "longLifetime", "futureIat", "futureNbf", "expired", "nbfAfterExpiry"})
    void rejectsMalformedOrOverbroadIdentityAndTimeBounds(String scenario) {
        var builder = token();
        switch (scenario) {
            case "owner" -> builder.claim("account_id", "1-1-1-1-1");
            case "subject" -> builder.subject("svc:auth-service");
            case "scope" -> builder.claim("scope", "hardware:sync:write-extra");
            case "scopeList" -> builder.claim("scope", List.of(HardwareSyncDeviceAccess.PERMISSION));
            case "ownerList" -> builder.claim("account_id", List.of(OWNER.toString()));
            case "blankKid" -> builder.header("kid", " ");
            case "blankJti" -> builder.jti(" ");
            case "audience" -> builder.audience(List.of("ltz-api"));
            case "issuer" -> builder.issuer("other");
            case "longLifetime" -> builder.expiresAt(NOW.plusSeconds(61));
            case "futureIat" -> builder.issuedAt(NOW.plusSeconds(31)).expiresAt(NOW.plusSeconds(91));
            case "futureNbf" -> builder.notBefore(NOW.plusSeconds(31));
            case "expired" -> builder.issuedAt(NOW.minusSeconds(91)).notBefore(NOW.minusSeconds(91)).expiresAt(NOW.minusSeconds(31));
            default -> builder.notBefore(NOW.plusSeconds(61));
        }
        assertThat(rejected(builder.build())).isTrue();
    }

    @Test
    void acceptsOnlyThirtySecondsOfClockSkewAndEnforcesTheUserNinetySecondWindow() {
        assertThat(rejected(token().issuedAt(NOW.minusSeconds(89)).notBefore(NOW.minusSeconds(89))
                .expiresAt(NOW.minusSeconds(29)).build())).isFalse();
        assertThat(rejected(token().issuedAt(NOW.plusSeconds(29)).notBefore(NOW.plusSeconds(29))
                .expiresAt(NOW.plusSeconds(89)).build())).isFalse();
        var validator = HardwareJwtValidators.validator("ltz-auth", "ltz-api", false, CLOCK);
        var user = token().header("typ", "at+jwt").subject(OWNER.toString()).audience(List.of("ltz-api"));
        assertThat(validator.validate(user.expiresAt(NOW.plusSeconds(61)).build()).hasErrors()).isTrue();
        assertThat(validator.validate(user.issuedAt(NOW.minusSeconds(91)).notBefore(NOW.minusSeconds(91))
                .expiresAt(NOW.minusSeconds(31)).build()).hasErrors()).isTrue();
        assertThat(validator.validate(user.issuedAt(NOW.minusSeconds(89)).notBefore(NOW.minusSeconds(89))
                .expiresAt(NOW.minusSeconds(29)).build()).hasErrors()).isFalse();
    }
}

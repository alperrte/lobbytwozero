package com.ltz.performance.infrastructure.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtTypeValidator;
import org.springframework.security.oauth2.jwt.MappedJwtClaimSetConverter;

/** Kullanıcı (at+jwt) ve servis (svc+jwt) token'ları için Auth sözleşmesine birebir doğrulama. */
public final class PerformanceJwtValidators {
    public static final Duration CLOCK_SKEW = Duration.ofSeconds(30);
    public static final Duration MAX_LIFETIME = Duration.ofSeconds(60);
    static final OAuth2TokenValidatorResult INVALID = OAuth2TokenValidatorResult.failure(
            new OAuth2Error("invalid_token", "Token validation failed", null));

    private PerformanceJwtValidators() { }

    public static Map<String, Object> requiredRawClaims(Map<String, Object> claims) {
        for (String claim : List.of("exp", "nbf", "iat", "sub", "aud", "iss")) {
            if (claims.get(claim) == null) throw new BadJwtException("Required token claim missing");
        }
        return MappedJwtClaimSetConverter.withDefaults(Map.of()).convert(claims);
    }

    public static OAuth2TokenValidator<Jwt> validator(String issuer, String audience, boolean service, Clock clock) {
        var timestamp = new JwtTimestampValidator(CLOCK_SKEW);
        timestamp.setClock(clock);
        return new DelegatingOAuth2TokenValidator<>(List.of(timestamp, new JwtIssuerValidator(issuer),
                new JwtTypeValidator(service ? "svc+jwt" : "at+jwt"), jwt -> shape(jwt, audience, service, clock)));
    }

    private static OAuth2TokenValidatorResult shape(Jwt jwt, String audience, boolean service, Clock clock) {
        try {
            boolean valid = allowedContext(jwt, audience, service) && allowedLifetime(jwt, clock)
                    && (service ? serviceIdentity(jwt, clock) : canonicalUuid(jwt.getSubject()));
            return valid ? OAuth2TokenValidatorResult.success() : INVALID;
        } catch (RuntimeException _) {
            return INVALID;
        }
    }

    private static boolean allowedContext(Jwt jwt, String audience, boolean service) {
        Object keyId = jwt.getHeaders().get("kid");
        var audiences = jwt.getAudience();
        return keyId instanceof String id && !id.isBlank() && id.length() <= 256
                && audiences != null && audiences.contains(audience) && (!service || audiences.size() == 1);
    }

    private static boolean allowedLifetime(Jwt jwt, Clock clock) {
        Instant issued = jwt.getIssuedAt();
        Instant expires = jwt.getExpiresAt();
        Instant notBefore = jwt.getNotBefore();
        return issued != null && expires != null && notBefore != null
                && expires.isAfter(issued) && !notBefore.isAfter(expires)
                && !issued.isAfter(clock.instant().plus(CLOCK_SKEW))
                && Duration.between(issued, expires).compareTo(MAX_LIFETIME) <= 0;
    }

    private static boolean serviceIdentity(Jwt jwt, Clock clock) {
        String subject = jwt.getSubject();
        String tokenId = jwt.getId();
        Instant expires = jwt.getExpiresAt();
        return subject != null && subject.startsWith("svc:") && tokenId != null && !tokenId.isBlank()
                && expires != null && !expires.isAfter(clock.instant().plus(MAX_LIFETIME).plus(CLOCK_SKEW));
    }

    public static boolean canonicalUuid(String value) {
        try { return UUID.fromString(value).toString().equalsIgnoreCase(value); }
        catch (RuntimeException _) { return false; }
    }
}

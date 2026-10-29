package com.ltz.hardware.infrastructure.security;

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

public final class HardwareJwtValidators {
    public static final Duration CLOCK_SKEW = Duration.ofSeconds(30);
    public static final Duration MAX_USER_LIFETIME = Duration.ofSeconds(60);
    private static final OAuth2TokenValidatorResult INVALID = OAuth2TokenValidatorResult.failure(
            new OAuth2Error("invalid_token", "Token validation failed", null));

    private HardwareJwtValidators() { }

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
            Object kid = jwt.getHeaders().get("kid");
            var audiences = jwt.getAudience();
            Instant issued = jwt.getIssuedAt();
            Instant expires = jwt.getExpiresAt();
            Instant notBefore = jwt.getNotBefore();
            String subject = jwt.getSubject();
            String tokenId = jwt.getId();
            Duration limit = service ? Duration.ofSeconds(60) : MAX_USER_LIFETIME;
            boolean valid = kid instanceof String id && !id.isBlank() && id.length() <= 256
                    && audiences != null && audiences.contains(audience)
                    && (!service || audiences.size() == 1)
                    && issued != null && expires != null && notBefore != null
                    && expires.isAfter(issued) && !notBefore.isAfter(expires)
                    && !issued.isAfter(clock.instant().plus(CLOCK_SKEW))
                    && Duration.between(issued, expires).compareTo(limit) <= 0;
            if (service) {
                valid = valid && subject != null && subject.startsWith("svc:")
                        && tokenId != null && !tokenId.isBlank()
                        && !expires.isAfter(clock.instant().plus(limit).plus(CLOCK_SKEW));
            } else {
                valid = valid && canonicalUuid(subject);
            }
            return valid ? OAuth2TokenValidatorResult.success() : INVALID;
        } catch (RuntimeException _) {
            return INVALID;
        }
    }

    public static boolean canonicalUuid(String value) {
        try { return UUID.fromString(value).toString().equalsIgnoreCase(value); }
        catch (RuntimeException _) { return false; }
    }
}

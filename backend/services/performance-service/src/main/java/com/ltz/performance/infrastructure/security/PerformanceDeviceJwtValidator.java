package com.ltz.performance.infrastructure.security;

import com.ltz.performance.application.port.PerformanceSyncDeviceAccess;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;

/**
 * Auth cihaz token'ını (dev+jwt) doğrular. Yalnız hedefi performance-service olan, tek audience'lı ve
 * performance:sync:write kapsamlı token geçer; başka servis için üretilmiş (ör. hardware-service) token reddedilir.
 */
public final class PerformanceDeviceJwtValidator {
    private PerformanceDeviceJwtValidator() { }

    public static OAuth2TokenValidator<Jwt> validator(String issuer, Clock clock) {
        var timestamp = new JwtTimestampValidator(PerformanceJwtValidators.CLOCK_SKEW);
        timestamp.setClock(clock);
        return new DelegatingOAuth2TokenValidator<>(timestamp, new JwtIssuerValidator(issuer),
                jwt -> shape(jwt, clock));
    }

    private static OAuth2TokenValidatorResult shape(Jwt jwt, Clock clock) {
        try {
            var issued = jwt.getIssuedAt();
            var expires = jwt.getExpiresAt();
            var notBefore = jwt.getNotBefore();
            var audiences = jwt.getAudience();
            Object kid = jwt.getHeaders().get("kid");
            Object account = jwt.getClaim("account_id");
            Object scope = jwt.getClaim("scope");
            boolean valid = "dev+jwt".equals(jwt.getHeaders().get("typ"))
                    && kid instanceof String id && !id.isBlank() && id.length() <= 256
                    && audiences != null && audiences.size() == 1
                    && audiences.contains(PerformanceSyncDeviceAccess.AUDIENCE)
                    && PerformanceJwtValidators.canonicalUuid(jwt.getSubject())
                    && account instanceof String owner && PerformanceJwtValidators.canonicalUuid(owner)
                    && scope instanceof String scopes && Arrays.asList(scopes.split(" +"))
                            .contains(PerformanceSyncDeviceAccess.PERMISSION)
                    && jwt.getId() != null && !jwt.getId().isBlank()
                    && issued != null && expires != null && notBefore != null
                    && expires.isAfter(issued) && !notBefore.isAfter(expires)
                    && !issued.isAfter(clock.instant().plus(PerformanceJwtValidators.CLOCK_SKEW))
                    && Duration.between(issued, expires).compareTo(PerformanceJwtValidators.MAX_LIFETIME) <= 0;
            return valid ? OAuth2TokenValidatorResult.success() : PerformanceJwtValidators.INVALID;
        } catch (RuntimeException _) {
            return PerformanceJwtValidators.INVALID;
        }
    }
}

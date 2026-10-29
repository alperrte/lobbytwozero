package com.ltz.hardware.infrastructure.security;

import com.ltz.hardware.application.port.HardwareSyncDeviceAccess;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;

/** Auth cihaz kimliğini doğrular; Performance aktif cihaz kararı ayrıca zorunludur. */
public final class HardwareDeviceJwtValidator {
    private static final Duration MAX_LIFETIME = Duration.ofSeconds(60);
    private static final OAuth2TokenValidatorResult INVALID = OAuth2TokenValidatorResult.failure(
            new OAuth2Error("invalid_token", "Token validation failed", null));

    private HardwareDeviceJwtValidator() { }

    public static OAuth2TokenValidator<Jwt> validator(String issuer, Clock clock) {
        var timestamp = new JwtTimestampValidator(HardwareJwtValidators.CLOCK_SKEW);
        timestamp.setClock(clock);
        return new DelegatingOAuth2TokenValidator<>(timestamp, new JwtIssuerValidator(issuer),
                jwt -> shape(jwt, clock));
    }

    private static OAuth2TokenValidatorResult shape(Jwt jwt, Clock clock) {
        try {
            var issued = jwt.getIssuedAt();
            var expires = jwt.getExpiresAt();
            var notBefore = jwt.getNotBefore();
            Object kid = jwt.getHeaders().get("kid");
            Object account = jwt.getClaim("account_id");
            Object scope = jwt.getClaim("scope");
            boolean valid = "dev+jwt".equals(jwt.getHeaders().get("typ"))
                    && kid instanceof String id && !id.isBlank() && id.length() <= 256
                    && jwt.getAudience().contains("hardware-service")
                    && HardwareJwtValidators.canonicalUuid(jwt.getSubject())
                    && account instanceof String owner && HardwareJwtValidators.canonicalUuid(owner)
                    && scope instanceof String scopes && Arrays.asList(scopes.split(" +"))
                            .contains(HardwareSyncDeviceAccess.PERMISSION)
                    && jwt.getId() != null && !jwt.getId().isBlank()
                    && issued != null && expires != null && notBefore != null
                    && expires.isAfter(issued) && !notBefore.isAfter(expires)
                    && !issued.isAfter(clock.instant().plus(HardwareJwtValidators.CLOCK_SKEW))
                    && Duration.between(issued, expires).compareTo(MAX_LIFETIME) <= 0;
            return valid ? OAuth2TokenValidatorResult.success() : INVALID;
        } catch (RuntimeException _) {
            return INVALID;
        }
    }
}

package com.ltz.auth;

import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtTypeValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import com.ltz.auth.infrastructure.security.TokenClaims;

/**
 * Yalnız test: hedef servisin servis token'ında ({@code svc+jwt}) uygulaması ZORUNLU denetimler
 * (docs-auth/servis-sozlesmeleri.md §3). Auth'un ürettiği token'ın sözleşmeyi karşıladığını bu sınıfla kanıtlarız;
 * hedef servisler aynı kuralları kendi doğrulayıcılarında uygular.
 */
public final class ServiceTokenContract {

    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);
    private static final Duration MAX_LIFETIME = Duration.ofSeconds(60);

    private ServiceTokenContract() {
    }

    /** @throws org.springframework.security.oauth2.jwt.JwtException sözleşmeye uymayan token için */
    public static Jwt verify(String token, RSAPublicKey key, String issuer, String audience, UUID pathAccountId,
                             Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(key)
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .validateType(false)
                .build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator(CLOCK_SKEW);
        timestamps.setClock(clock);
        List<OAuth2TokenValidator<Jwt>> rules = List.of(
                timestamps,
                new JwtIssuerValidator(issuer),
                new JwtTypeValidator(TokenClaims.SERVICE_TOKEN_TYPE),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                        tokenAudience -> tokenAudience != null && tokenAudience.equals(List.of(audience))),
                new JwtClaimValidator<String>(JwtClaimNames.SUB, TokenClaims.AUTH_SERVICE_SUBJECT::equals),
                new JwtClaimValidator<Object>(JwtClaimNames.JTI, Objects::nonNull),
                new JwtClaimValidator<String>(TokenClaims.SCOPE, scope -> scope != null
                        && Arrays.asList(scope.split(" ")).contains(TokenClaims.ACCOUNT_ERASE_SCOPE)),
                new JwtClaimValidator<String>(TokenClaims.ACCOUNT_ID, pathAccountId.toString()::equals),
                maxLifetime(clock));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(rules));
        return decoder.decode(token);
    }

    /** {@code Authorization: Bearer <token>} başlığından token'ı alır. */
    public static String bearerToken(String authorization) {
        String prefix = "Bearer ";
        if (authorization == null || !authorization.startsWith(prefix)) {
            throw new IllegalArgumentException("Missing bearer token");
        }
        return authorization.substring(prefix.length());
    }

    private static OAuth2TokenValidator<Jwt> maxLifetime(Clock clock) {
        OAuth2TokenValidatorResult failure = OAuth2TokenValidatorResult.failure(
                new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "Token lifetime is too long", null));
        return jwt -> {
            if (jwt.getIssuedAt() == null || jwt.getExpiresAt() == null) {
                return failure;
            }
            boolean lifetimeOk = Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).compareTo(MAX_LIFETIME) <= 0;
            boolean expiresSoon = !jwt.getExpiresAt().isAfter(clock.instant().plus(MAX_LIFETIME).plus(CLOCK_SKEW));
            return lifetimeOk && expiresSoon ? OAuth2TokenValidatorResult.success() : failure;
        };
    }
}

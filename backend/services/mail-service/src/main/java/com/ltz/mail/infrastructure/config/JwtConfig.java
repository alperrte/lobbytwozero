package com.ltz.mail.infrastructure.config;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtTypeValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import com.ltz.mail.infrastructure.security.TokenClaims;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.BadJWTException;
import com.nimbusds.jwt.proc.JWTClaimsSetVerifier;

/**
 * Servis token'larını Auth Service'in JWKS anahtarıyla bu serviste yeniden doğrular (Gateway'e güvenilmez). Yalnız RS256
 * kabul edilir (alg=none/HS256 reddedilir). Mail Service'in tek token türü {@code svc+jwt}'dir: {@code typ}, {@code iss},
 * {@code aud=mail-service}, {@code sub=svc:auth-service}, {@code jti}, {@code iat} zorunlu ve ömür en fazla 60 sn
 * (docs-auth/servis-sozlesmeleri.md §3). Kullanıcı token'ı ({@code at+jwt}) {@code typ} nedeniyle reddedilir. Kapsam
 * ({@code scope}) yetkilendirme kuralında denetlenir.
 */
@Configuration(proxyBeanMethods = false)
public class JwtConfig {

    /** Küçük saat sapması toleransı (rehber: en fazla 60 sn). */
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    /**
     * Spring, {@code iat} yoksa kendiliğinden {@code exp - 1 sn} atar; bu yüzden Spring doğrulayıcıları {@code iat}'in
     * gerçekten var olduğunu göremez. Eksiklik imza çözülürken, Spring varsayılanı atanmadan önce reddedilir.
     */
    private static final JWTClaimsSetVerifier<SecurityContext> REQUIRE_ISSUED_AT = (claims, context) -> {
        if (claims.getIssueTime() == null) {
            throw new BadJWTException("The iat claim is required");
        }
    };

    @Bean
    JwtDecoder serviceTokenDecoder(AuthProperties properties, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwksUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                // typ, doğrulayıcı içinde açıkça denetlenir (svc+jwt).
                .validateType(false)
                .jwtProcessorCustomizer(processor -> processor.setJWTClaimsSetVerifier(REQUIRE_ISSUED_AT))
                .build();
        decoder.setJwtValidator(serviceTokenValidator(properties.issuer(), properties.serviceAudience(), clock));
        return decoder;
    }

    /** İmza dışındaki tüm servis token kuralları; JWKS'siz de test edilebilsin diye ayrıdır. */
    public static OAuth2TokenValidator<Jwt> serviceTokenValidator(String issuer, String serviceAudience, Clock clock) {
        JwtTimestampValidator timestamps = new JwtTimestampValidator(CLOCK_SKEW);
        timestamps.setClock(clock);
        List<OAuth2TokenValidator<Jwt>> rules = new ArrayList<>(List.of(
                timestamps,
                new JwtIssuerValidator(issuer),
                new JwtTypeValidator(TokenClaims.SERVICE_TOKEN_TYPE),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                        audience -> audience != null && audience.contains(serviceAudience)),
                new JwtClaimValidator<Object>(JwtClaimNames.EXP, Objects::nonNull),
                new JwtClaimValidator<Object>(JwtClaimNames.IAT, Objects::nonNull),
                new JwtClaimValidator<Object>(JwtClaimNames.JTI, Objects::nonNull),
                // Mail'e yalnız Auth istek atar; başka bir servis kimliği (svc:<başka>) geçerli imzayla bile reddedilir.
                new JwtClaimValidator<String>(JwtClaimNames.SUB, TokenClaims.AUTH_SERVICE_SUBJECT::equals)));
        rules.add(maxLifetime(TokenClaims.SERVICE_TOKEN_MAX_LIFETIME, clock));
        return new DelegatingOAuth2TokenValidator<>(rules);
    }

    /**
     * Token'ın ömrü {@code limit}'i aşamaz. Spring {@code iat} yoksa {@code exp - 1 sn} atadığı için yalnız
     * {@code exp - iat} yetmez; bu yüzden ayrıca bitiş zamanı şimdiden en fazla {@code limit} (+ saat sapması)
     * ileride olmalıdır.
     */
    private static OAuth2TokenValidator<Jwt> maxLifetime(Duration limit, Clock clock) {
        OAuth2TokenValidatorResult failure = OAuth2TokenValidatorResult.failure(
                new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "Token lifetime is too long", null));
        return jwt -> {
            if (jwt.getIssuedAt() == null || jwt.getExpiresAt() == null) {
                return failure;
            }
            boolean lifetimeOk = Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).compareTo(limit) <= 0;
            boolean expiresSoon = !jwt.getExpiresAt().isAfter(clock.instant().plus(limit).plus(CLOCK_SKEW));
            return lifetimeOk && expiresSoon ? OAuth2TokenValidatorResult.success() : failure;
        };
    }
}

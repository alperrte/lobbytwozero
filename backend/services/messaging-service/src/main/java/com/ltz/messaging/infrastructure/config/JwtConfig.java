package com.ltz.messaging.infrastructure.config;

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

import com.ltz.messaging.infrastructure.security.TokenClaims;

/**
 * Auth Service'in token'larını bu serviste JWKS anahtarıyla yeniden doğrular (Gateway'e güvenilmez); yalnız RS256.
 * <ul>
 *   <li><b>Kullanıcı access token'ı</b> ({@code /api/v1/messaging/**}): docs-auth/entegrasyon/README.md bölüm 1;
 *       {@code typ=at+jwt}, {@code iss=ltz-auth}, {@code aud} içinde {@code ltz-api}; {@code exp}, {@code nbf},
 *       {@code iat}, {@code sub} zorunlu.</li>
 *   <li><b>Servis token'ı</b> ({@code /internal/**}, docs-auth/servis-sozlesmeleri.md §3): {@code typ=svc+jwt},
 *       {@code iss=ltz-auth}, {@code aud} bu servisin adı, {@code sub=svc:<çağıran>}, {@code jti} zorunlu, ömür en çok
 *       60 sn. Kapsam ({@code scope}) yetkilendirme kuralında, {@code sub} tam eşleşmesi ve {@code account_id} uçta
 *       denetlenir.</li>
 * </ul>
 * Saat farkı payı 30 sn. İki token türü birbirinin uçlarında geçmez.
 */
@Configuration(proxyBeanMethods = false)
public class JwtConfig {

    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    @Bean
    JwtDecoder userTokenDecoder(AuthProperties properties, Clock clock) {
        return decoder(properties.jwksUri(), accessTokenValidator(properties.issuer(), properties.audience(), clock));
    }

    @Bean
    JwtDecoder serviceTokenDecoder(AuthProperties properties, Clock clock) {
        return decoder(properties.jwksUri(),
                serviceTokenValidator(properties.issuer(), properties.serviceAudience(), clock));
    }

    private static JwtDecoder decoder(String jwksUri, OAuth2TokenValidator<Jwt> validator) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                // typ, doğrulayıcı içinde açıkça denetlenir (at+jwt / svc+jwt).
                .validateType(false)
                .build();
        decoder.setJwtValidator(validator);
        return decoder;
    }

    /** İmza dışındaki tüm kullanıcı token kuralları; JWKS sunucusu olmadan da test edilebilsin diye ayrıdır. */
    public static OAuth2TokenValidator<Jwt> accessTokenValidator(String issuer, String audience, Clock clock) {
        List<OAuth2TokenValidator<Jwt>> rules = new ArrayList<>(
                commonRules(issuer, audience, clock, TokenClaims.ACCESS_TOKEN_TYPE));
        rules.add(new JwtClaimValidator<Object>(JwtClaimNames.NBF, Objects::nonNull));
        return new DelegatingOAuth2TokenValidator<>(rules);
    }

    /** Servis token'ı kuralları (imza dışında). */
    public static OAuth2TokenValidator<Jwt> serviceTokenValidator(String issuer, String serviceAudience, Clock clock) {
        List<OAuth2TokenValidator<Jwt>> rules = new ArrayList<>(
                commonRules(issuer, serviceAudience, clock, TokenClaims.SERVICE_TOKEN_TYPE));
        rules.add(new JwtClaimValidator<String>(JwtClaimNames.SUB,
                subject -> subject != null && subject.startsWith(TokenClaims.SERVICE_SUBJECT_PREFIX)));
        rules.add(new JwtClaimValidator<Object>(JwtClaimNames.JTI, Objects::nonNull));
        rules.add(maxLifetime(TokenClaims.SERVICE_TOKEN_MAX_LIFETIME, clock));
        return new DelegatingOAuth2TokenValidator<>(rules);
    }

    private static List<OAuth2TokenValidator<Jwt>> commonRules(String issuer, String audience, Clock clock,
                                                              String tokenType) {
        JwtTimestampValidator timestamps = new JwtTimestampValidator(CLOCK_SKEW);
        timestamps.setClock(clock);
        return List.of(
                timestamps,
                new JwtIssuerValidator(issuer),
                new JwtTypeValidator(tokenType),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                        values -> values != null && values.contains(audience)),
                new JwtClaimValidator<Object>(JwtClaimNames.EXP, Objects::nonNull),
                new JwtClaimValidator<Object>(JwtClaimNames.IAT, Objects::nonNull),
                new JwtClaimValidator<Object>(JwtClaimNames.SUB, Objects::nonNull));
    }

    /**
     * Ömür {@code limit}'i aşamaz. Spring {@code iat} yoksa kendiliğinden {@code exp - 1 sn} atadığından yalnız
     * {@code exp - iat} yetmez: bitiş zamanı da şimdiden en çok {@code limit} (+ saat payı) ileride olmalıdır.
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

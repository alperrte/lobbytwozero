package com.ltz.user.infrastructure.config;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

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

import com.ltz.user.infrastructure.security.TokenClaims;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.BadJWTException;
import com.nimbusds.jwt.proc.JWTClaimsSetVerifier;

/**
 * Token'ları Auth Service'in JWKS anahtarıyla bu serviste yeniden doğrular (Gateway'e güvenilmez). Yalnız RS256 kabul
 * edilir (alg=none/HS256 reddedilir). İki ayrı token türü vardır ve {@code typ} başlığı ile birbirinden ayrılır:
 * <ul>
 *   <li>Kullanıcı access token'ı ({@code at+jwt}): {@code /api/v1/user/**} uçları için.</li>
 *   <li>Servis token'ı ({@code svc+jwt}, D4 sözleşmesi): yalnız {@code /internal/**} uçları için.</li>
 * </ul>
 * Kullanıcı kuralları Auth'un kendi doğrulayıcısıyla aynıdır (docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md §3.2); tek
 * ek kural {@code sub} değerinin UUID olmasıdır, çünkü {@code sub} doğrudan profil anahtarıdır.
 */
@Configuration(proxyBeanMethods = false)
public class JwtConfig {

    /** Küçük saat sapması toleransı (rehber: en fazla 60 sn). */
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

    /**
     * Spring, {@code iat} yoksa kendiliğinden {@code exp - 1 sn} atar; bu yüzden Spring doğrulayıcıları {@code iat}'in
     * <b>gerçekten var olduğunu</b> göremez. Auth her token'a {@code iat} koyar (rehber §3.2: zorunlu), bu yüzden
     * eksiklik imza çözülürken, Spring varsayılanı atanmadan önce reddedilir.
     */
    private static final JWTClaimsSetVerifier<SecurityContext> REQUIRE_ISSUED_AT = (claims, context) -> {
        if (claims.getIssueTime() == null) {
            throw new BadJWTException("The iat claim is required");
        }
    };

    private static JwtDecoder decoder(String jwksUri, OAuth2TokenValidator<Jwt> validator) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                // typ, doğrulayıcı içinde açıkça denetlenir (at+jwt / svc+jwt).
                .validateType(false)
                .jwtProcessorCustomizer(processor -> processor.setJWTClaimsSetVerifier(REQUIRE_ISSUED_AT))
                .build();
        decoder.setJwtValidator(validator);
        return decoder;
    }

    /** Signature dışındaki tüm kullanıcı token kuralları; JWKS'siz de test edilebilsin diye ayrıdır. */
    public static OAuth2TokenValidator<Jwt> accessTokenValidator(String issuer, String audience, Clock clock) {
        List<OAuth2TokenValidator<Jwt>> rules = new ArrayList<>(
                commonRules(issuer, audience, clock, TokenClaims.ACCESS_TOKEN_TYPE));
        rules.add(new JwtClaimValidator<String>(JwtClaimNames.SUB, JwtConfig::isUuid));
        return new DelegatingOAuth2TokenValidator<>(rules);
    }

    /**
     * Servis token'ı kuralları (docs-auth/servis-sozlesmeleri.md §3): {@code typ=svc+jwt}, {@code iss=ltz-auth},
     * {@code aud} bu servisin adı, {@code sub=svc:<çağıran>}, {@code jti} zorunlu ve ömür en fazla 60 sn. Kapsam
     * ({@code scope}) yetkilendirme kuralında denetlenir.
     */
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
                        tokenAudience -> tokenAudience != null && tokenAudience.contains(audience)),
                new JwtClaimValidator<Object>(JwtClaimNames.EXP, Objects::nonNull),
                // Auth'un doğrulayıcısıyla aynı kural. Not: Spring iat yoksa exp-1 atadığından bu kural tek başına iat'ın
                // varlığını kanıtlamaz; ömür kısıtı gereken yerde (servis token'ı) bitiş zamanı ayrıca denetlenir.
                new JwtClaimValidator<Object>(JwtClaimNames.IAT, Objects::nonNull),
                new JwtClaimValidator<Object>(JwtClaimNames.SUB, Objects::nonNull));
    }

    /**
     * Token'ın ömrü {@code limit}'i aşamaz. Spring {@code iat} yoksa kendiliğinden {@code exp - 1 sn} atadığı için
     * yalnız {@code exp - iat} yetmez; bu yüzden ayrıca bitiş zamanı şimdiden en fazla {@code limit} (+ saat sapması)
     * ileride olmalıdır. İkisi birlikte, {@code iat} çıkarılarak ömür kuralını aşmayı engeller.
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

    private static boolean isUuid(String value) {
        if (value == null) {
            return false;
        }
        try {
            return UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException _) {
            return false;
        }
    }
}

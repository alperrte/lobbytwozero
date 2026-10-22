package com.ltz.social.infrastructure.config;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtTypeValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import com.ltz.social.infrastructure.security.TokenClaims;

/**
 * Access token'ı Auth Service'in JWKS anahtarıyla bu serviste yeniden doğrular (Gateway'e güvenilmez). Yalnız RS256
 * kabul edilir (alg=none/HS256 reddedilir); iss, aud, typ, exp, sub zorunludur. Kural listesi Auth'un kendi
 * doğrulayıcısıyla aynıdır; fazladan kural eklenmez.
 */
@Configuration(proxyBeanMethods = false)
public class JwtConfig {

    /** Küçük saat sapması toleransı (rehber: en fazla 60 sn). */
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    @Bean
    JwtDecoder jwtDecoder(AuthProperties properties, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwksUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                // typ, accessTokenValidator içinde "at+jwt" olarak açıkça doğrulanır.
                .validateType(false)
                .build();
        decoder.setJwtValidator(accessTokenValidator(properties.issuer(), properties.audience(), clock));
        return decoder;
    }

    /** Signature dışındaki tüm access token kuralları; JWKS'siz de test edilebilsin diye ayrıdır. */
    public static OAuth2TokenValidator<Jwt> accessTokenValidator(String issuer, String audience, Clock clock) {
        JwtTimestampValidator timestamps = new JwtTimestampValidator(CLOCK_SKEW);
        timestamps.setClock(clock);
        List<OAuth2TokenValidator<Jwt>> validators = List.of(
                timestamps,
                new JwtIssuerValidator(issuer),
                new JwtTypeValidator(TokenClaims.ACCESS_TOKEN_TYPE),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                        tokenAudience -> tokenAudience != null && tokenAudience.contains(audience)),
                new JwtClaimValidator<Object>(JwtClaimNames.EXP, Objects::nonNull),
                new JwtClaimValidator<Object>(JwtClaimNames.SUB, Objects::nonNull));
        return new DelegatingOAuth2TokenValidator<>(validators);
    }
}

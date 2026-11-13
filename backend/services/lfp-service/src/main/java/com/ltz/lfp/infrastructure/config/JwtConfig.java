package com.ltz.lfp.infrastructure.config;

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

import com.ltz.lfp.infrastructure.security.TokenClaims;

/**
 * Kullanıcı access token'ını ({@code at+jwt}) Auth Service'in JWKS anahtarıyla bu serviste yeniden doğrular (Gateway'e
 * güvenilmez). Yalnız RS256 kabul edilir (alg=none/HS256 reddedilir). Kurallar Auth'un kendi doğrulayıcısıyla aynıdır
 * (docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md §3.2); fazladan kural eklenmez.
 */
@Configuration(proxyBeanMethods = false)
public class JwtConfig {

    /** Küçük saat sapması toleransı (rehber: en fazla 60 sn). */
    static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    @Bean
    JwtDecoder userTokenDecoder(AuthProperties properties, Clock clock) {
        return decoder(properties.jwksUri(), accessTokenValidator(properties.issuer(), properties.audience(), clock));
    }

    static JwtDecoder decoder(String jwksUri, OAuth2TokenValidator<Jwt> validator) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                // typ, doğrulayıcı içinde açıkça denetlenir (at+jwt).
                .validateType(false)
                .build();
        decoder.setJwtValidator(validator);
        return decoder;
    }

    /** Signature dışındaki tüm kullanıcı token kuralları; JWKS'siz de test edilebilsin diye ayrıdır. */
    public static OAuth2TokenValidator<Jwt> accessTokenValidator(String issuer, String audience, Clock clock) {
        return new DelegatingOAuth2TokenValidator<>(commonRules(issuer, audience, clock, TokenClaims.ACCESS_TOKEN_TYPE));
    }

    static List<OAuth2TokenValidator<Jwt>> commonRules(String issuer, String audience, Clock clock, String tokenType) {
        JwtTimestampValidator timestamps = new JwtTimestampValidator(CLOCK_SKEW);
        timestamps.setClock(clock);
        return List.of(
                timestamps,
                new JwtIssuerValidator(issuer),
                new JwtTypeValidator(tokenType),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                        tokenAudience -> tokenAudience != null && tokenAudience.contains(audience)),
                new JwtClaimValidator<Object>(JwtClaimNames.EXP, Objects::nonNull),
                new JwtClaimValidator<Object>(JwtClaimNames.IAT, Objects::nonNull),
                new JwtClaimValidator<Object>(JwtClaimNames.SUB, Objects::nonNull));
    }
}

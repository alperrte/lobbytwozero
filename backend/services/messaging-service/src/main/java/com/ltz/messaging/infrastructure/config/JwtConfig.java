package com.ltz.messaging.infrastructure.config;

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

import com.ltz.messaging.infrastructure.security.TokenClaims;

/**
 * Kullanıcı access token'ını Auth Service'in JWKS anahtarıyla bu serviste yeniden doğrular (Gateway'e güvenilmez).
 * Kurallar docs-auth/entegrasyon/README.md bölüm 1'dekiyle aynıdır: yalnız RS256, {@code typ=at+jwt},
 * {@code iss=ltz-auth}, {@code aud} içinde {@code ltz-api}; {@code exp}, {@code nbf}, {@code iat}, {@code sub} zorunlu,
 * saat farkı payı 30 sn. Servis token'ı ({@code svc+jwt}) bu doğrulayıcıdan geçmez; Messaging'in henüz iç ucu yoktur.
 */
@Configuration(proxyBeanMethods = false)
public class JwtConfig {

    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    @Bean
    JwtDecoder jwtDecoder(AuthProperties properties, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwksUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                // typ, doğrulayıcı içinde açıkça denetlenir.
                .validateType(false)
                .build();
        decoder.setJwtValidator(accessTokenValidator(properties.issuer(), properties.audience(), clock));
        return decoder;
    }

    /** İmza dışındaki tüm kurallar; JWKS sunucusu olmadan da test edilebilsin diye ayrıdır. */
    public static OAuth2TokenValidator<Jwt> accessTokenValidator(String issuer, String audience, Clock clock) {
        JwtTimestampValidator timestamps = new JwtTimestampValidator(CLOCK_SKEW);
        timestamps.setClock(clock);
        return new DelegatingOAuth2TokenValidator<>(List.<OAuth2TokenValidator<Jwt>>of(
                timestamps,
                new JwtIssuerValidator(issuer),
                new JwtTypeValidator(TokenClaims.ACCESS_TOKEN_TYPE),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                        values -> values != null && values.contains(audience)),
                new JwtClaimValidator<Object>(JwtClaimNames.EXP, Objects::nonNull),
                new JwtClaimValidator<Object>(JwtClaimNames.NBF, Objects::nonNull),
                new JwtClaimValidator<Object>(JwtClaimNames.IAT, Objects::nonNull),
                new JwtClaimValidator<Object>(JwtClaimNames.SUB, Objects::nonNull)));
    }
}

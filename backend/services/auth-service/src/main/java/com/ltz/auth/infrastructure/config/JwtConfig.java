package com.ltz.auth.infrastructure.config;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtTypeValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.MappedJwtClaimSetConverter;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;

import com.ltz.auth.infrastructure.security.JwtSigningKey;
import com.ltz.auth.infrastructure.security.ServiceClientTokenValidator;
import com.ltz.auth.infrastructure.security.SessionStateValidator;
import com.ltz.auth.infrastructure.security.TokenClaims;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

/**
 * D1: RS256 + JWKS. Doğrulayıcı yalnız RS256 kabul eder (alg=none/HS256 karışıklığı reddedilir) ve
 * iss, aud, exp, iat, sub, typ, kid ile oturum durumunu zorunlu denetler.
 */
@Configuration(proxyBeanMethods = false)
public class JwtConfig {

    @Bean
    JwtSigningKey jwtSigningKey(JwtProperties properties) {
        return JwtSigningKey.fromBase64Pkcs8(properties.privateKey(), properties.additionalPublicKeys());
    }

    @Bean
    JwtEncoder jwtEncoder(JwtSigningKey signingKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(signingKey.jwk())));
    }

    @Bean
    JwtDecoder jwtDecoder(JwtSigningKey signingKey, JwtProperties properties,
                          SessionStateValidator sessionStateValidator, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(signingKey.publicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                // typ, aşağıda "at+jwt" olarak açıkça doğrulanır.
                .validateType(false)
                .build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator();
        timestamps.setClock(clock);
        List<OAuth2TokenValidator<Jwt>> validators = List.of(
                timestamps,
                new JwtIssuerValidator(properties.issuer()),
                new JwtTypeValidator(TokenClaims.ACCESS_TOKEN_TYPE),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                        audience -> audience != null && audience.contains(properties.audience())),
                new JwtClaimValidator<Object>(JwtClaimNames.EXP, Objects::nonNull),
                new JwtClaimValidator<Object>(JwtClaimNames.IAT, Objects::nonNull),
                new JwtClaimValidator<Object>(JwtClaimNames.SUB, Objects::nonNull),
                keyIdValidator(signingKey.keyId()),
                sessionStateValidator);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
        return decoder;
    }

    /**
     * Servis -> Auth iç çağrıları için ayrı doğrulama: kullanıcı doğrulayıcısından ve JwtDecoder bean'inden bağımsızdır
     * (typ svc+jwt, aud auth-service, kayıtlı istemci ve kapsam). Yetkiler {@code scope} claim'inden {@code SCOPE_} önekiyle çıkar.
     */
    @Bean("serviceTokenAuthenticationManager")
    AuthenticationManager serviceTokenAuthenticationManager(JwtSigningKey signingKey, JwtProperties properties,
                                                            ServiceClientProperties clients, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(signingKey.publicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .validateType(false)
                .build();
        // Varsayılan claim dönüştürücü eksik iat'ı exp'ten üretebilir; ham iat zorunludur.
        MappedJwtClaimSetConverter converter = MappedJwtClaimSetConverter.withDefaults(Map.of());
        decoder.setClaimSetConverter(claims -> {
            if (claims.get(JwtClaimNames.IAT) == null) {
                throw new BadJwtException("Service token requires iat");
            }
            return converter.convert(claims);
        });
        JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ofSeconds(30));
        timestamps.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps,
                new JwtIssuerValidator(properties.issuer()),
                new ServiceClientTokenValidator(clients, clock, signingKey.keyId())));
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(decoder);
        provider.setJwtAuthenticationConverter(new JwtAuthenticationConverter());
        return new ProviderManager(provider);
    }

    private static OAuth2TokenValidator<Jwt> keyIdValidator(String expectedKeyId) {
        OAuth2TokenValidatorResult failure = OAuth2TokenValidatorResult.failure(
                new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "Unknown signing key", null));
        return jwt -> expectedKeyId.equals(jwt.getHeaders().get("kid")) ? OAuth2TokenValidatorResult.success() : failure;
    }
}

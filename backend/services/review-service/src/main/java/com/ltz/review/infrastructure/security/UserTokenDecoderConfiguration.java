package com.ltz.review.infrastructure.security;

import java.net.URI;
import java.net.http.HttpClient;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.MappedJwtClaimSetConverter;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration(proxyBeanMethods = false)
public class UserTokenDecoderConfiguration {
    private HttpClient jwksClient;

    @Bean("userJwtDecoder")
    JwtDecoder userJwtDecoder(@Value("${ltz.security.jwt.jwk-set-uri:}") String jwks,
            @Value("${ltz.security.jwt.issuer:ltz-auth}") String issuer,
            @Value("${JWT_AUDIENCE:ltz-api}") String audience) {
        if (jwks.isBlank()) return token -> { throw new BadJwtException("İmza kaynağı eksik."); };
        URI uri = URI.create(jwks);
        if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || issuer.isBlank() || audience.isBlank())
            throw new IllegalStateException("Geçersiz kullanıcı JWT yapılandırması.");
        var decoder = NimbusJwtDecoder.withJwkSetUri(jwks).jwsAlgorithm(SignatureAlgorithm.RS256)
                .restOperations(jwksOperations()).validateType(false).build();
        var claims = MappedJwtClaimSetConverter.withDefaults(Map.of());
        decoder.setClaimSetConverter(raw -> {
            if (raw.get("iat") == null || raw.get("nbf") == null || raw.get("exp") == null)
                throw new BadJwtException("Zaman alanları eksik.");
            return claims.convert(raw);
        });
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtIssuerValidator(issuer),
                new JwtTimestampValidator(UserTokenPolicy.CLOCK_SKEW), contract(audience)));
        return decoder;
    }

    static OAuth2TokenValidator<Jwt> contract(String audience) {
        return contract(audience, Clock.systemUTC());
    }

    static OAuth2TokenValidator<Jwt> contract(String audience, Clock clock) {
        return jwt -> {
            boolean valid = "at+jwt".equals(jwt.getHeaders().get("typ"))
                    && jwt.getHeaders().get("kid") instanceof String key && !key.isBlank()
                    && jwt.getAudience().contains(audience) && canonicalSubject(jwt.getSubject())
                    && jwt.getIssuedAt() != null && jwt.getNotBefore() != null && jwt.getExpiresAt() != null
                    && !jwt.getIssuedAt().isAfter(clock.instant().plus(UserTokenPolicy.CLOCK_SKEW))
                    && jwt.getExpiresAt().isAfter(jwt.getIssuedAt())
                    && !jwt.getExpiresAt().isAfter(jwt.getIssuedAt().plus(UserTokenPolicy.MAX_ACCESS_TTL));
            return valid ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
        };
    }

    private static boolean canonicalSubject(String subject) {
        if (subject == null) return false;
        try {
            return UUID.fromString(subject).toString().equalsIgnoreCase(subject);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private synchronized RestTemplate jwksOperations() {
        if (jwksClient == null) jwksClient = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofMillis(750))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(jwksClient);
        factory.setReadTimeout(java.time.Duration.ofMillis(750));
        return new RestTemplate(factory);
    }

    @PreDestroy
    synchronized void closeClient() {
        if (jwksClient != null) {
            jwksClient.close();
            jwksClient = null;
        }
    }
}

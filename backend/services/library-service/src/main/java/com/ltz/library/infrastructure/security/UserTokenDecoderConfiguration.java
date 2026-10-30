package com.ltz.library.infrastructure.security;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
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
import org.springframework.security.oauth2.jwt.JwtTypeValidator;
import org.springframework.security.oauth2.jwt.MappedJwtClaimSetConverter;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestTemplate;

/** Auth access token sözleşmesi; D4 servis token doğrulayıcısından bağımsızdır. */
@Configuration(proxyBeanMethods = false)
public class UserTokenDecoderConfiguration {
    @Bean(destroyMethod = "close")
    HttpClient userJwksHttpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Bean("userJwtDecoder")
    JwtDecoder userJwtDecoder(@Value("${ltz.security.jwt.jwk-set-uri:}") String jwks,
            @Value("${ltz.security.jwt.issuer:ltz-auth}") String issuer,
            @Value("${ltz.security.jwt.audience:ltz-api}") String audience, HttpClient userJwksHttpClient) {
        if (jwks.isBlank()) return token -> { throw new BadJwtException("JWT doğrulaması yapılandırılmamış."); };
        URI uri = URI.create(jwks);
        if (uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) || issuer.isBlank() || audience.isBlank())
            throw new IllegalStateException("JWT yapılandırması geçersiz.");
        var factory = new JdkClientHttpRequestFactory(userJwksHttpClient);
        factory.setReadTimeout(Duration.ofSeconds(5));
        var decoder = NimbusJwtDecoder.withJwkSetUri(jwks).jwsAlgorithm(SignatureAlgorithm.RS256)
                .restOperations(new RestTemplate(factory)).validateType(false).build();
        var claimsConverter = MappedJwtClaimSetConverter.withDefaults(Map.of());
        decoder.setClaimSetConverter(claims -> {
            if (claims.get("iat") == null) throw new BadJwtException("Token zamanı eksik.");
            return claimsConverter.convert(claims);
        });
        OAuth2TokenValidator<Jwt> contract = jwt -> validContract(jwt, audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Geçersiz kullanıcı token'ı.", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtIssuerValidator(issuer),
                new JwtTypeValidator("at+jwt"), new JwtTimestampValidator(Duration.ofSeconds(30)), contract));
        return decoder;
    }

    private static boolean validContract(Jwt jwt, String audience) {
        return validSubject(jwt.getSubject()) && jwt.getAudience() != null && jwt.getAudience().contains(audience)
                && jwt.getHeaders().get("kid") instanceof String kid && !kid.isBlank()
                && jwt.getExpiresAt() != null && jwt.getNotBefore() != null && jwt.getIssuedAt() != null
                && !jwt.getIssuedAt().isAfter(Instant.now().plusSeconds(30));
    }

    private static boolean validSubject(String subject) {
        try { return subject != null && UUID.fromString(subject).toString().equalsIgnoreCase(subject); }
        catch (IllegalArgumentException ignored) { return false; }
    }

    static List<org.springframework.security.core.GrantedAuthority> authorities(Jwt token) {
        Object roles = token.getClaim("roles");
        if (!(roles instanceof List<?> values) || !values.stream().allMatch(String.class::isInstance)) return List.of();
        List<String> allowed = List.of("PLAYER", "CONTENT_CREATOR", "GAME_DEVELOPER", "MODERATOR", "ADMIN");
        return values.stream().filter(allowed::contains)
                .<org.springframework.security.core.GrantedAuthority>map(role ->
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role)).toList();
    }
}

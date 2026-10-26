package com.ltz.game.infrastructure.security;

import java.net.URI;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
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

/** D4: kullanıcı doğrulayıcısından bağımsız, yalnız kısa ömürlü servis token'ları. */
@Configuration(proxyBeanMethods = false)
public class ServiceTokenDecoderConfiguration {
    private static final Duration SKEW = Duration.ofSeconds(30);

    @Bean("serviceJwtDecoder")
    JwtDecoder serviceJwtDecoder(
            @Value("${ltz.security.jwt.issuer:ltz-auth}") String issuer,
            @Value("${ltz.security.jwt.jwk-set-uri:}") String jwks,
            @Value("${ltz.security.jwt.public-key:}") String publicKey,
            @Value("${ltz.game.auth.service-audience:game-service}") String audience) {
        if (!"game-service".equals(audience) || issuer.isBlank())
            throw new IllegalStateException("Servis JWT issuer/audience yapılandırması geçersiz.");
        boolean hasJwks = !jwks.isBlank();
        boolean hasPublicKey = !publicKey.isBlank();
        if (hasJwks && hasPublicKey)
            throw new IllegalStateException("JWT için yalnız bir imza kaynağı kullanılabilir.");
        if (!hasJwks && !hasPublicKey)
            return token -> { throw new BadJwtException("JWT imza kaynağı yapılandırılmamış."); };
        NimbusJwtDecoder decoder = hasJwks
                ? NimbusJwtDecoder.withJwkSetUri(checkedUri(jwks)).jwsAlgorithm(SignatureAlgorithm.RS256)
                        .restOperations(jwksClient()).validateType(false).build()
                : NimbusJwtDecoder.withPublicKey(parsePublicKey(publicKey)).signatureAlgorithm(SignatureAlgorithm.RS256).validateType(false).build();
        var converter = MappedJwtClaimSetConverter.withDefaults(Map.of());
        decoder.setClaimSetConverter(claims -> {
            // Varsayılan converter eksik iat'ı exp üzerinden üretebilir: ham claim zorunludur.
            if (claims.get("iat") == null) throw new BadJwtException("Servis token'ında iat zorunludur.");
            return converter.convert(claims);
        });
        decoder.setJwtValidator(validator(issuer, audience, Clock.systemUTC()));
        return decoder;
    }

    static OAuth2TokenValidator<Jwt> validator(String issuer, String audience, Clock clock) {
        var timestamps = new JwtTimestampValidator(SKEW);
        timestamps.setClock(clock);
        OAuth2TokenValidator<Jwt> contract = jwt -> {
            var now = clock.instant();
            var issued = jwt.getIssuedAt();
            var expires = jwt.getExpiresAt();
            boolean valid = "svc+jwt".equals(jwt.getHeaders().get("typ"))
                    && jwt.getHeaders().get("kid") instanceof String kid && !kid.isBlank()
                    && List.of(audience).equals(jwt.getAudience())
                    && jwt.getSubject() != null && jwt.getSubject().startsWith("svc:")
                    && jwt.getId() != null && !jwt.getId().isBlank()
                    && jwt.getNotBefore() != null && issued != null && expires != null
                    && !issued.isAfter(now.plus(SKEW)) && expires.isAfter(issued)
                    && Duration.between(issued, expires).compareTo(Duration.ofSeconds(60)) <= 0
                    && !expires.isAfter(now.plusSeconds(60).plus(SKEW));
            return valid ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Geçersiz servis token'ı.", null));
        };
        return new DelegatingOAuth2TokenValidator<>(new JwtIssuerValidator(issuer), timestamps, contract);
    }

    private static org.springframework.web.client.RestOperations jwksClient() {
        var client = java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofMillis(750))
                .followRedirects(java.net.http.HttpClient.Redirect.NEVER).build();
        var factory = new org.springframework.http.client.JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofMillis(750));
        return new org.springframework.web.client.RestTemplate(factory);
    }

    private static String checkedUri(String value) {
        URI uri = URI.create(value);
        if (uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())))
            throw new IllegalStateException("Geçersiz Auth JWKS adresi.");
        return value;
    }

    private static RSAPublicKey parsePublicKey(String pem) {
        try {
            String body = pem.replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "").replaceAll("\\s", "");
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(body)));
        } catch (Exception _) {
            throw new IllegalStateException("Geçersiz RSA açık anahtarı.");
        }
    }
}

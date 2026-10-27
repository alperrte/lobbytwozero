package com.ltz.game.infrastructure.security;

import java.net.URI;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtTypeValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/** JWT doğrulama kurulumu: imza, algoritma allowlist, süre, issuer, audience ve kullanıcı kimliği biçimi. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtSecurityProperties.class)
public class JwtDecoderConfiguration {
    @Bean
    @org.springframework.context.annotation.Primary
    JwtDecoder jwtDecoder(JwtSecurityProperties properties) {
        if (!properties.hasJwkSetUri() && !properties.hasPublicKey()) return new FailClosedJwtDecoder();
        if (properties.hasJwkSetUri() && properties.hasPublicKey())
            throw new IllegalStateException("JWT imza kaynağı olarak yalnız biri yapılandırılabilir.");
        if (isBlank(properties.issuer()) || isBlank(properties.audience()))
            throw new IllegalStateException("JWT issuer ve audience, imza kaynağıyla birlikte zorunludur.");
        if (!"RS256".equals(properties.algorithm()))
            throw new IllegalStateException("Desteklenmeyen JWT imza algoritması.");
        if (!"sub".equals(properties.userIdClaim()) || !"roles".equals(properties.rolesClaim()))
            throw new IllegalStateException("Auth kimlik ve rol claim adları sub ve roles olmalıdır.");
        if (properties.clockSkew() == null || properties.clockSkew().isNegative()
                || properties.clockSkew().compareTo(Duration.ofSeconds(60)) > 0)
            throw new IllegalStateException("JWT saat toleransı 0–60 saniye olmalıdır.");
        SignatureAlgorithm algorithm = Objects.requireNonNull(SignatureAlgorithm.from(properties.algorithm()), "JWT imza algoritması");
        NimbusJwtDecoder decoder = properties.hasJwkSetUri()
                ? NimbusJwtDecoder.withJwkSetUri(checkedUri(properties.jwkSetUri())).jwsAlgorithm(algorithm).validateType(false).build()
                : NimbusJwtDecoder.withPublicKey(parseRsaPublicKey(properties.publicKey())).signatureAlgorithm(algorithm).validateType(false).build();
        decoder.setJwtValidator(validator(properties));
        return decoder;
    }

    static OAuth2TokenValidator<Jwt> validator(JwtSecurityProperties properties) {
        return new DelegatingOAuth2TokenValidator<>(List.of(
                new JwtTimestampValidator(properties.clockSkew()),
                new JwtTypeValidator("at+jwt"),
                new JwtClaimValidator<Object>("exp", Objects::nonNull),
                new JwtClaimValidator<Object>("nbf", Objects::nonNull),
                new JwtClaimValidator<Instant>("iat", issued -> issued != null && !issued.isAfter(Instant.now().plus(properties.clockSkew()))),
                new JwtIssuerValidator(properties.issuer()),
                new JwtClaimValidator<List<String>>("aud", audience -> audience != null && audience.contains(properties.audience())),
                keyIdPresent(), subjectIsUuid("sub")));
    }

    private static OAuth2TokenValidator<Jwt> keyIdPresent() {
        return jwt -> jwt.getHeaders().get("kid") instanceof String id && !id.isBlank()
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Geçersiz anahtar kimliği.", null));
    }

    private static OAuth2TokenValidator<Jwt> subjectIsUuid(String claim) {
        return jwt -> {
            try {
                String subject = jwt.getClaimAsString(claim);
                if (!UUID.fromString(subject).toString().equalsIgnoreCase(subject)) throw new IllegalArgumentException();
                return OAuth2TokenValidatorResult.success();
            } catch (RuntimeException _) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Geçersiz kullanıcı kimliği.", null));
            }
        };
    }

    private static String checkedUri(String value) {
        URI uri = URI.create(value);
        if (uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())))
            throw new IllegalStateException("JWT JWKS adresi geçerli bir http(s) adresi olmalıdır.");
        return value;
    }

    private static RSAPublicKey parseRsaPublicKey(String pem) {
        try {
            String body = pem.replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "").replaceAll("\\s", "");
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(body)));
        } catch (Exception _) {
            throw new IllegalStateException("JWT açık anahtarı geçerli bir PEM X.509 RSA anahtarı değil.");
        }
    }

    private static boolean isBlank(String value) { return value == null || value.isBlank(); }
}

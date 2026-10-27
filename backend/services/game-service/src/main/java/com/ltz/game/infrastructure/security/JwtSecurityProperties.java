package com.ltz.game.infrastructure.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Auth RS256/at+jwt doğrulama sözleşmesi. Üretimde imza kaynağı Auth JWKS'tir.
 * İmza kaynağı yoksa hiçbir token kabul edilmez; publicKey izole testlerde kullanılabilir.
 *
 * @param issuer        beklenen {@code iss}; imza kaynağı verilmişse zorunludur
 * @param audience      beklenen {@code aud}; imza kaynağı verilmişse zorunludur
 * @param jwkSetUri     Auth Service JWKS adresi (publicKey ile birlikte verilemez)
 * @param publicKey     PEM X.509 RSA açık anahtarı (jwkSetUri ile birlikte verilemez; açık anahtar gizli değildir)
 * @param algorithm     yalnız RS256
 * @param userIdClaim   yalnız sub (UUID)
 * @param rolesClaim    yalnız roles (metin listesi)
 * @param clockSkew     süre doğrulama toleransı (0–60 saniye)
 */
@ConfigurationProperties("ltz.security.jwt")
public record JwtSecurityProperties(
        String issuer,
        String audience,
        String jwkSetUri,
        String publicKey,
        @DefaultValue("RS256") String algorithm,
        @DefaultValue("sub") String userIdClaim,
        @DefaultValue("roles") String rolesClaim,
        @DefaultValue("60s") Duration clockSkew) {
    boolean hasJwkSetUri() { return jwkSetUri != null && !jwkSetUri.isBlank(); }
    boolean hasPublicKey() { return publicKey != null && !publicKey.isBlank(); }
}

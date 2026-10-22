package com.ltz.game.infrastructure.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * JWT doğrulama sözleşmesi. Değerlerin tamamı yapılandırılabilir; kodda sabit anahtar, issuer veya audience yoktur.
 * Hiçbir imza kaynağı verilmezse hiçbir token kabul edilir (fail-closed). Auth Service gerçek sözleşmesini
 * yayınladığında yalnız bu özellikler (ve gerekirse {@link LtzJwtAuthenticationConverter}) güncellenir.
 *
 * @param issuer        beklenen {@code iss}; imza kaynağı verilmişse zorunludur
 * @param audience      beklenen {@code aud}; imza kaynağı verilmişse zorunludur
 * @param jwkSetUri     Auth Service JWKS adresi (publicKey ile birlikte verilemez)
 * @param publicKey     PEM X.509 RSA açık anahtarı (jwkSetUri ile birlikte verilemez; açık anahtar gizli değildir)
 * @param algorithm     izinli imza algoritması (yalnız RS256/RS384/RS512)
 * @param userIdClaim   kullanıcı kimliği (UUID) claim adı
 * @param rolesClaim    rol listesi claim adı (dizi veya boşluk/virgül ayrımlı metin)
 * @param clockSkew     süre doğrulama toleransı
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

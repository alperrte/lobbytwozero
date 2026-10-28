package com.ltz.auth.infrastructure.config;

import java.time.Duration;
import java.util.List;

import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Access token sözleşmesi (D1). privateKey: Base64 kodlu PKCS#8 DER RSA özel anahtarı (AUTH_JWT_PRIVATE_KEY,
 * en az 3072 bit). Uzunluk ve biçim, değer hata mesajına düşmesin diye anahtar yüklenirken denetlenir.
 * additionalPublicKeys: JWKS'te imza anahtarının yanında yayınlanan, YALNIZ DOĞRULAMA için ek açık anahtarlar (Base64
 * X.509 DER, en az 3072 bit). Anahtar rotasyonunda yeni anahtarı imzalamadan önce önceden yayınlamak ve eski anahtarı
 * tüketicilerin önbelleği boşalana kadar yayında tutmak içindir. Auth bu anahtarlarla imzalanmış token'ı kabul etmez.
 */
@Validated
@ConfigurationProperties("ltz.auth.jwt")
public record JwtProperties(
        @NotBlank String issuer,
        @NotBlank String audience,
        @NotNull @DurationMin(minutes = 1) @DurationMax(minutes = 15) Duration accessTokenTtl,
        @NotBlank String privateKey,
        List<String> additionalPublicKeys) {

    @ConstructorBinding
    public JwtProperties {
        additionalPublicKeys = additionalPublicKeys == null ? List.of() : List.copyOf(additionalPublicKeys);
    }

    /**
     * Dört parametreli ESKİ imza korunur: Game, Hardware, Library ve Review'ın sözleşme testleri bu sınıfı gerçek Auth kaynağından
     * derleyip bu yapıcıyı yansımayla çağırır. İmzayı değiştirmek onların testlerini kırar; bu yüzden kaldırılmaz.
     */
    public JwtProperties(String issuer, String audience, Duration accessTokenTtl, String privateKey) {
        this(issuer, audience, accessTokenTtl, privateKey, null);
    }

    @Override
    public String toString() {
        return "JwtProperties[issuer=" + issuer + ", audience=" + audience + ", accessTokenTtl=" + accessTokenTtl
                + ", privateKey=***]";
    }
}

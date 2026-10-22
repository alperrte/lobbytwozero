package com.ltz.auth.infrastructure.config;

import java.time.Duration;

import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Access token sözleşmesi (D1). privateKey: Base64 kodlu PKCS#8 DER RSA özel anahtarı (AUTH_JWT_PRIVATE_KEY,
 * en az 3072 bit). Uzunluk ve biçim, değer hata mesajına düşmesin diye anahtar yüklenirken denetlenir.
 */
@Validated
@ConfigurationProperties("ltz.auth.jwt")
public record JwtProperties(
        @NotBlank String issuer,
        @NotBlank String audience,
        @NotNull @DurationMin(minutes = 1) @DurationMax(minutes = 15) Duration accessTokenTtl,
        @NotBlank String privateKey) {

    @Override
    public String toString() {
        return "JwtProperties[issuer=" + issuer + ", audience=" + audience + ", accessTokenTtl=" + accessTokenTtl
                + ", privateKey=***]";
    }
}

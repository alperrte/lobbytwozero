package com.ltz.auth.infrastructure.config;

import java.time.Duration;

import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * AUTH-02 doğrulama kodu politikası. Kod uzunluğu (6) gereksinim sabitidir ve burada ayarlanamaz.
 *
 * @param hmacKey Base64 kodlu, en az 32 baytlık gizli anahtar (AUTH_OTP_HMAC_KEY). Uzunluk kontrolü,
 *                değerin hata mesajına düşmemesi için Bean Validation ile değil hasher'da yapılır.
 */
@Validated
@ConfigurationProperties("ltz.auth.verification")
public record VerificationProperties(
        @NotNull @DurationMin(minutes = 1) @DurationMax(minutes = 30) Duration codeTtl,
        @Min(1) @Max(10) int maxAttempts,
        @NotNull @DurationMin(seconds = 1) Duration resendCooldown,
        @Min(1) int dailyLimit,
        @NotBlank String hmacKey) {

    @Override
    public String toString() {
        return "VerificationProperties[codeTtl=" + codeTtl + ", maxAttempts=" + maxAttempts
                + ", resendCooldown=" + resendCooldown + ", dailyLimit=" + dailyLimit + ", hmacKey=***]";
    }
}

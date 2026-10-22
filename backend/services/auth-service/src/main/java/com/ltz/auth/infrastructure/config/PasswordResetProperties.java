package com.ltz.auth.infrastructure.config;

import java.net.URI;
import java.time.Duration;

import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

/**
 * Parola sıfırlama politikası (AUTH-10). linkBaseUrl: e-postadaki bağlantının SABİT tabanı; token URL
 * fragment'ine (#token=…) eklenir, böylece sunucu log'larına ve Referer'a gitmez. requestCooldown: aynı hesap için
 * iki e-posta arasındaki en kısa süre (bu sürede gelen istek sessizce yok sayılır).
 */
@Validated
@ConfigurationProperties("ltz.auth.password-reset")
public record PasswordResetProperties(
        @NotNull @DurationMin(minutes = 5) @DurationMax(hours = 2) Duration tokenTtl,
        @NotNull @DurationMin(seconds = 1) Duration requestCooldown,
        @NotNull URI linkBaseUrl) {
}

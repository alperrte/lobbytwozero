package com.ltz.auth.infrastructure.config;

import java.time.Duration;

import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Yalnız dev profilinde bağlanır (bkz. {@link DevSmtpMailConfig}). Değerler application-dev.properties'te;
 * IDE'den çalıştırırken host, LTZ_AUTH_MAIL_DEVSMTP_HOST ortam değişkeniyle değiştirilebilir.
 */
@Validated
@ConfigurationProperties("ltz.auth.mail.dev-smtp")
public record DevSmtpProperties(
        @NotBlank String host,
        @Min(1) @Max(65535) int port,
        @NotNull @DurationMin(millis = 1) Duration connectTimeout,
        @NotNull @DurationMin(millis = 1) Duration readTimeout,
        @NotBlank @Email String from) {
}

package com.ltz.auth.infrastructure.config;

import java.time.Duration;

import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

/**
 * Oturum politikası. refreshTokenTtl: kullanılmayan refresh token'ın ömrü; absoluteTtl: yenilemelerle
 * uzatılamayan oturum üst sınırı; refreshReuseGrace: döndürülmüş token'ın eşzamanlı yenileme sayıldığı süre.
 * secureCookies prod profilinde kapatılamaz.
 */
@Validated
@ConfigurationProperties("ltz.auth.session")
public record SessionProperties(
        @NotNull @DurationMin(hours = 1) @DurationMax(days = 30) Duration refreshTokenTtl,
        @NotNull @DurationMin(hours = 1) @DurationMax(days = 90) Duration absoluteTtl,
        @NotNull @DurationMin(seconds = 1) @DurationMax(minutes = 1) Duration refreshReuseGrace,
        boolean secureCookies) {
}

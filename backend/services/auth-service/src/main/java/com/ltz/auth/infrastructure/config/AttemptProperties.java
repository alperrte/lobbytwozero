package com.ltz.auth.infrastructure.config;

import java.time.Duration;

import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Parola ve 2FA denemeleri için brute-force sınırı (AUTH-13): pencerede maxFailures hata → lockDuration kilit. */
@Validated
@ConfigurationProperties("ltz.auth.attempts")
public record AttemptProperties(
        @Min(1) @Max(20) int maxFailures,
        @NotNull @DurationMin(minutes = 1) Duration window,
        @NotNull @DurationMin(minutes = 1) Duration lockDuration) {
}

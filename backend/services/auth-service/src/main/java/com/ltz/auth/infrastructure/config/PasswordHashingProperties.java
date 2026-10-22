package com.ltz.auth.infrastructure.config;

import java.time.Duration;

import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Argon2id her hash için ~19 MiB bellek kullanır; eşzamanlı hash sayısı sınırlanarak
 * public kayıt ucu üzerinden bellek tüketme saldırısı engellenir.
 */
@Validated
@ConfigurationProperties("ltz.auth.password-hashing")
public record PasswordHashingProperties(
        @Min(1) int maxConcurrentHashes,
        @NotNull @DurationMin(millis = 1) Duration acquireTimeout) {
}

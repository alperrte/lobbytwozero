package com.ltz.auth.infrastructure.config;

import java.time.Duration;

import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Süreli hesap askısı (B8). {@code maxDuration}: yöneticinin verebileceği en uzun askı (daha uzunu süresiz askıdır ve
 * bitiş verilmeden istenir); {@code minDuration}: bitiş en az bu kadar ilerde olmalı (yanlışlıkla geçmiş/anlık tarih
 * girilmesini önler). {@code workerEnabled=false}: otomatik açma kapalı (testlerde elle tetiklenir).
 */
@Validated
@ConfigurationProperties("ltz.auth.suspension")
public record SuspensionProperties(
        boolean workerEnabled,
        @NotNull @DurationMin(seconds = 5) @DurationMax(hours = 1) Duration pollInterval,
        @Min(1) @Max(1000) int batchSize,
        @NotNull @DurationMin(minutes = 1) @DurationMax(hours = 24) Duration minDuration,
        @NotNull @DurationMin(days = 1) @DurationMax(days = 3650) Duration maxDuration) {
}

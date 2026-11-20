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
 * Hesap durumu olaylarının onarımı. Durumu {@code window} içinde değişmiş ama bir hedef için olay satırı olmayan hesaplar
 * (hedef o sırada kapalıydı ya da satır kayboldu) gerçek değişim zamanıyla yeniden gönderilir. {@code grace}: normal
 * akışın olayı yazması için tanınan süre; daha yeni değişiklikler bu turda atlanır.
 */
@Validated
@ConfigurationProperties("ltz.auth.events.reconcile")
public record EventReconcileProperties(
        boolean workerEnabled,
        @NotNull @DurationMin(seconds = 10) @DurationMax(hours = 6) Duration pollInterval,
        @NotNull @DurationMin(hours = 1) @DurationMax(days = 7) Duration window,
        @NotNull @DurationMin(minutes = 1) @DurationMax(hours = 6) Duration grace,
        @Min(1) @Max(500) int batchSize) {
}

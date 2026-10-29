package com.ltz.auth.infrastructure.config;

import java.time.Duration;
import java.util.Set;

import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Auth'un diğer servislere ilettiği olaylar (AUTH-19). Varsayılan hedef listesi <b>boştur</b>: hedef tanımlanana kadar
 * hiçbir olay yazılmaz veya gönderilmez. Bir hedefi açmak, o servisin {@code POST /internal/events} ucunu ve Auth'ta
 * {@code svc:auth-service} için {@code events:ingest} kabulünü yazmış olmasını gerektirir.
 *
 * @param targets          olay alacak servis adları (ör. {@code user}, {@code community}); adresleri
 *                         {@code ltz.auth.deletion.service-urls} ile aynıdır
 * @param workerEnabled    aktarıcı iş parçacığı; testlerde elle tetiklenir
 * @param deliveredRetention teslim edilmiş satırların saklanma süresi
 */
@Validated
@ConfigurationProperties("ltz.auth.events")
public record EventProperties(
        Set<String> targets,
        boolean workerEnabled,
        @NotNull @DurationMin(seconds = 1) Duration pollInterval,
        @Min(1) @Max(500) int batchSize,
        @NotNull @DurationMin(seconds = 10) Duration lease,
        @NotNull @DurationMin(seconds = 1) Duration initialBackoff,
        @NotNull @DurationMin(seconds = 1) Duration maxBackoff,
        @Min(1) @Max(1000) int stuckAttempts,
        @NotNull @DurationMin(days = 1) @DurationMax(days = 90) Duration deliveredRetention,
        @NotNull @DurationMin(millis = 100) @DurationMax(seconds = 30) Duration httpTimeout) {

    public EventProperties {
        targets = targets == null ? Set.of() : Set.copyOf(targets);
    }
}

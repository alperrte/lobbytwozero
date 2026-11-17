package com.ltz.messaging.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Outbox teslimi (MSG-16) ve saklama süresi. Teslim hedefi {@code adapter} ile seçilir: {@code unavailable}
 * (varsayılan; notification-service alıcı ucu ve Auth'ta messaging → notification çifti yok, olaylar bekler) ya da
 * {@code notification}. Adres ortak {@code NOTIFICATION_SERVICE_URL} değerinden gelir; yeni ENV yoktur.
 */
@Validated
@ConfigurationProperties("ltz.messaging.outbox")
public record OutboxProperties(
        @NotBlank String adapter,
        @NotBlank String notificationServiceUrl,
        @NotNull Duration pollInterval,
        /** Bir turda alınacak en fazla olay. */
        @Min(1) @Max(500) int batchSize,
        /** Alınan olayın kilit süresi; relay çökerse olay bu süreden sonra yeniden alınır. */
        @NotNull Duration lease,
        /** Bu denemeden sonra olay FAILED olur ve el ile müdahale bekler. */
        @Min(1) @Max(100) int maxAttempts,
        @NotNull Duration baseBackoff,
        @NotNull Duration maxBackoff,
        /** Bu süreden eski olaylar durumdan bağımsız silinir (eski bir "yeni mesaj" bildiriminin değeri yoktur). */
        @NotNull Duration retention,
        @NotNull Duration cleanupInterval,
        /** Bir temizlik turunda silinecek en fazla olay (uzun kilit tutulmaz). */
        @Min(1) @Max(100_000) int cleanupBatchSize) {

    public static final String NOTIFICATION_ADAPTER = "notification";
}

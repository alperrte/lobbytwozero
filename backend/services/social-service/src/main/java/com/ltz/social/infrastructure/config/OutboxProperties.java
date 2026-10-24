package com.ltz.social.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Outbox relay ayarları. Relay, gerçek bir teslim adaptörü yazılana kadar varsayılan olarak kapalıdır. */
@Validated
@ConfigurationProperties("ltz.social.outbox")
public record OutboxProperties(
        /** Zamanlanmış relay çalışsın mı. */
        boolean relayEnabled,
        @NotNull Duration pollInterval,
        /** Bir turda alınacak en fazla olay. */
        @Min(1) @Max(500) int batchSize,
        /** Alınan olayın kilit süresi; relay çökerse olay bu süreden sonra yeniden alınır. */
        @NotNull Duration lease,
        /** Bu denemeden sonra olay FAILED olur ve el ile müdahale bekler. */
        @Min(1) @Max(100) int maxAttempts,
        @NotNull Duration baseBackoff,
        @NotNull Duration maxBackoff) {
}

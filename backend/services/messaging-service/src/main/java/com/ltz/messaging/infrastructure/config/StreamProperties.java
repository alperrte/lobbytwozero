package com.ltz.messaging.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Gerçek zamanlı akış (SSE, MSG-09). {@code heartbeat}: bağlantıyı canlı tutan boş satır aralığı; Gateway'in proxy okuma
 * zaman aşımından (5 sn) kısa olmalıdır. {@code maxDuration}: bir bağlantının en uzun ömrü; ayrıca token'ın bitiş
 * zamanında kapatılır. {@code maxPerUser}: kullanıcı başına eşzamanlı akış sayısı.
 */
@Validated
@ConfigurationProperties("ltz.messaging.stream")
public record StreamProperties(@NotNull Duration heartbeat, @NotNull Duration maxDuration, @Min(1) int maxPerUser) {
}

package com.ltz.messaging.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;

/** Tepki sınırları (MSG-23). {@code maxPerMinute}: kullanıcı başına son 60 sn içinde tepki değişikliği sayısı. */
@Validated
@ConfigurationProperties("ltz.messaging.reactions")
public record ReactionProperties(@Min(1) int maxPerMinute) {
}

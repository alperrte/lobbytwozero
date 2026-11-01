package com.ltz.messaging.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Mesaj ve liste sınırları. {@code maxPerMinute}: kullanıcı başına son 60 sn içinde gönderilebilecek mesaj sayısı
 * (SECURITY.md §12). Sayfa boyutları sunucuda zorlanır.
 */
@Validated
@ConfigurationProperties("ltz.messaging.messages")
public record MessageProperties(
        @Min(1) int maxPerMinute,
        @Min(1) int defaultPageSize,
        @Min(1) @Max(200) int maxPageSize,
        @Min(1) int defaultConversationPageSize,
        @Min(1) @Max(100) int maxConversationPageSize) {
}

package com.ltz.social.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** user-service yansıması geçmiş aktarımı (tek seferlik, varsayılan kapalı). */
@Validated
@ConfigurationProperties("ltz.social.projection-backfill")
public record ProjectionBackfillProperties(
        /** Açılışta aktarım çalışsın mı. */
        boolean enabled,
        /** Bir seferde okunan ilişki sayısı. */
        @DefaultValue("200") @Min(1) @Max(1000) int batchSize) {
}

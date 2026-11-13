package com.ltz.lfp.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * LFP-15 süre dolumu zamanlayıcısı. Okumalar süresi geçmiş ilanı zaten {@code EXPIRED} gösterir; zamanlayıcı durumu
 * veritabanına kalıcı yazar (sahip güncellemesiyle yarışta sürüm artar).
 */
@Validated
@ConfigurationProperties("ltz.lfp.expiry")
public record ExpiryProperties(
        /** Zamanlayıcı açık mı (testler kapatıp servisi doğrudan çağırır). */
        boolean enabled,
        /** İki çalıştırma arasındaki bekleme. */
        @NotNull Duration interval,
        /** Tek transaction'da işaretlenecek en fazla ilan. */
        @Min(1) @Max(5000) int batchSize,
        /** Tek çalıştırmada en fazla parti (geri kalanı sonraki çalıştırmaya kalır). */
        @Min(1) @Max(1000) int maxBatchesPerRun) {
}

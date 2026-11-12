package com.ltz.messaging.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Görsel yükleme sınırları (MSG-17, SECURITY.md §12-13). Dosya boyutu sabit 1 MB'tır
 * ({@link com.ltz.messaging.domain.entity.MessageAttachment#MAX_SIZE_BYTES}); burada yalnız oran, bekleyen yükleme,
 * piksel ve eşzamanlılık sınırları vardır.
 *
 * @param maxPerMinute    kullanıcı başına son 60 sn'de en çok yükleme
 * @param maxPending      kullanıcı başına henüz mesaja bağlanmamış en çok yükleme
 * @param pendingTtl      bağlanmamış yüklemenin silinmeden önce bekleyebileceği süre
 * @param maxSide         PNG/JPEG en uzun kenar (piksel)
 * @param maxPixels       PNG/JPEG toplam piksel (çözme belleğini sınırlar)
 * @param maxGifSide      GIF en uzun kenar (piksel)
 * @param maxGifFrames    GIF en çok kare
 * @param maxGifPixels    GIF kenar x kenar x kare üst sınırı (istemcide çözme bombasını keser)
 * @param concurrency     aynı anda işlenen en çok görsel
 * @param acquireTimeout  işleme sırası beklenecek en uzun süre; sonra {@code MEDIA_BUSY}
 */
@Validated
@ConfigurationProperties("ltz.messaging.attachments")
public record AttachmentProperties(
        @Min(1) int maxPerMinute,
        @Min(1) int maxPending,
        @NotNull Duration pendingTtl,
        @Min(16) @Max(4096) int maxSide,
        @Min(256) long maxPixels,
        @Min(16) @Max(4096) int maxGifSide,
        @Min(1) @Max(500) int maxGifFrames,
        @Min(256) long maxGifPixels,
        @Min(1) @Max(8) int concurrency,
        @NotNull Duration acquireTimeout) {
}

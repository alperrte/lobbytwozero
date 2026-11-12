package com.ltz.messaging.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

/**
 * Sesli mesaj süre sınırları (MSG-21). Süre paketlerden hesaplanır; {@code durationTolerance}, kayıt 2:00'de
 * durdurulduğunda kodlayıcının ürettiği birkaç milisaniyelik fazlanın reddedilmemesi içindir. Sınırlar
 * {@code message_attachments.duration_ms} CHECK'inin (500–121000 ms) dışına çıkamaz. Dosya boyutu sabit 1 MB'tır.
 *
 * @param minDuration       en kısa kayıt
 * @param maxDuration       en uzun kayıt (ürün sınırı)
 * @param durationTolerance üst sınıra eklenen pay
 */
@Validated
@ConfigurationProperties("ltz.messaging.voice")
public record VoiceProperties(
        @NotNull Duration minDuration,
        @NotNull Duration maxDuration,
        @NotNull Duration durationTolerance) {

    /** {@code message_attachments.duration_ms} CHECK sınırları. */
    static final long DB_MIN_MILLIS = 500;
    static final long DB_MAX_MILLIS = 121_000;

    public VoiceProperties {
        if (minDuration != null && maxDuration != null && durationTolerance != null) {
            long min = minDuration.toMillis();
            long maxAccepted = maxDuration.plus(durationTolerance).toMillis();
            if (min < DB_MIN_MILLIS || durationTolerance.isNegative() || maxDuration.toMillis() < min
                    || maxAccepted > DB_MAX_MILLIS) {
                throw new IllegalArgumentException("voice duration limits must stay within 500-121000 ms");
            }
        }
    }

    /** Kabul edilen en uzun süre: {@code maxDuration + durationTolerance}. */
    public long maxAcceptedMillis() {
        return maxDuration.plus(durationTolerance).toMillis();
    }
}

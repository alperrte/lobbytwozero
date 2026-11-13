package com.ltz.lfp.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** İlan kuralları ve kötüye kullanım sınırları (LFP-15, SECURITY.md §12). */
@Validated
@ConfigurationProperties("ltz.lfp.listings")
public record ListingProperties(
        /** Süre verilmezse ilanın geçerlilik süresi. */
        @NotNull Duration defaultDuration,
        /** Seçilebilecek en kısa süre (DB kısıtı: en az 1 saat). */
        @NotNull Duration minDuration,
        /** Seçilebilecek en uzun süre (DB kısıtı: en çok 7 gün). */
        @NotNull Duration maxDuration,
        /** Planlanan oyun zamanının en uzun aralığı (başlangıç → bitiş). */
        @NotNull Duration maxPlayWindow,
        /** Kullanıcı başına aynı anda açık ilan. */
        @Min(1) @Max(50) int maxOpenPerOwner,
        /** Kullanıcı başına son bir saatte açılabilecek ilan. */
        @Min(1) @Max(100) int maxCreatedPerHour) {

    private static final Duration DB_MIN_DURATION = Duration.ofHours(1);
    private static final Duration DB_MAX_DURATION = Duration.ofDays(7);

    /** Ayar, veritabanı kısıtının ({@code ck_lfp_listings_expiry}) dışına çıkamaz; varsayılan aralığın içindedir. */
    @AssertTrue(message = "durations must satisfy 1h <= min <= default <= max <= 7d")
    public boolean isDurationRangeValid() {
        if (defaultDuration == null || minDuration == null || maxDuration == null) {
            return false;
        }
        return minDuration.compareTo(DB_MIN_DURATION) >= 0 && maxDuration.compareTo(DB_MAX_DURATION) <= 0
                && minDuration.compareTo(defaultDuration) <= 0 && defaultDuration.compareTo(maxDuration) <= 0;
    }
}

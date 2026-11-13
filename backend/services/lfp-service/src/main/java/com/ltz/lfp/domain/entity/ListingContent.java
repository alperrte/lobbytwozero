package com.ltz.lfp.domain.entity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.ltz.lfp.domain.enums.Platform;

/**
 * İlan sahibinin belirlediği alanlar (LFP-03..09), metin kuralları uygulanmış hâliyle. Oluşturma ve güncelleme aynı
 * değeri kullanır; zaman kuralları (geçmiş zaman, süre sonu) {@link Listing} içinde denetlenir çünkü "şimdi" ve ilanın
 * bitişini bilmek gerekir. Zamanlar PostgreSQL'in hassasiyetine (mikro saniye) yuvarlanır.
 */
public record ListingContent(Platform platform, String skillLevel, List<String> roles, int playersNeeded,
                             Instant playTimeStart, Instant playTimeEnd, boolean micRequired, String description) {

    /** Ham alanlardan metin kurallarını uygulayarak içerik kurar; kural ihlalinde {@code LfpException} atar. */
    public static ListingContent of(Platform platform, String skillLevel, List<String> roles, int playersNeeded,
                                    Instant playTimeStart, Instant playTimeEnd, boolean micRequired,
                                    String description) {
        return new ListingContent(platform, ListingText.skillLevel(skillLevel), ListingText.roles(roles),
                playersNeeded, micros(playTimeStart), micros(playTimeEnd), micRequired,
                ListingText.description(description));
    }

    private static Instant micros(Instant instant) {
        return instant == null ? null : instant.truncatedTo(ChronoUnit.MICROS);
    }
}

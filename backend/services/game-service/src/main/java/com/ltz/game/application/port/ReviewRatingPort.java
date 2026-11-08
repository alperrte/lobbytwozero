package com.ltz.game.application.port;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Review Service'in LTZ puan dışa aktarma ucu (Game → Review, yalnız servis token'ı). Game bu veriyi kendi puan
 * projeksiyonuna taşır; istek anında oyun başına veya sayfa başına Review çağrısı yapılmaz.
 */
public interface ReviewRatingPort {

    /**
     * Puanı olan oyunların {@code gameId} artan sırada bir sayfasını getirir.
     *
     * @param after dışlayıcı imleç; {@code null} ise baştan
     * @param limit sayfa büyüklüğü (1–500)
     * @throws com.ltz.game.domain.exception.ReviewRatingUnavailableException veri alınamadı veya yanıt geçersizse
     */
    RatingPage fetchPage(UUID after, int limit);

    /** En az bir puanı olan oyunun özeti: iki ondalıklı ortalama (1–10) ve benzersiz puan veren sayısı (≥1). */
    record RatingSummary(UUID gameId, BigDecimal averageRating, long ratingCount) { }

    /** {@code nextAfter} {@code null} ise son sayfadır. */
    record RatingPage(List<RatingSummary> items, UUID nextAfter) {
        public RatingPage {
            items = List.copyOf(items);
        }
    }
}

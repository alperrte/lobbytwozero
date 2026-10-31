package com.ltz.review.api.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Oyun bazlı LTZ istatistikleri. Sayısal puanlar, aktif metin incelemeleri ve tavsiyeler üç ayrı kaynaktır;
 * biri diğerinin sayısını artırmaz. Boş veri: sayımlar 0, dağılım 10 sıfırlı kova, ortalama/yüzdeler null.
 */
public record GameReviewStatisticsResponse(UUID gameId, Ratings ratings, Reviews reviews, Recommendations recommendations) {
    /** {@code ratingCount} puan veren benzersiz kullanıcı sayısıdır; kullanıcı/oyun başına tek puan vardır. */
    public record Ratings(long ratingCount, BigDecimal averageRating, List<Bucket> distribution) {}

    public record Bucket(int rating, long userCount) {}

    /** Yalnız VISIBLE metin incelemeleri; silinmişler sayılmaz. Puan veren kullanıcı sayısıyla ilgisi yoktur. */
    public record Reviews(long activeCount) {}

    /** Yüzdeler 0–100 ölçeğinde, iki ondalık, HALF_UP; payda {@code totalCount}, sıfırsa null. */
    public record Recommendations(long recommendedCount, long notRecommendedCount, long totalCount,
            BigDecimal recommendedPercentage, BigDecimal notRecommendedPercentage) {}
}

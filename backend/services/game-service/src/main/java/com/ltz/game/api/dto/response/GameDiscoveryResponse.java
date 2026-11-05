package com.ltz.game.api.dto.response;

import com.ltz.game.domain.enums.RatingStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * {@code ratingsSyncedAt}: LTZ puan projeksiyonunun son hatasız tamamlanan tarama zamanı; hiç tamamlanmadıysa
 * {@code null} (bu durumda her öğenin puan durumu {@code UNAVAILABLE}'dır).
 */
public record GameDiscoveryResponse(List<Item> items, int page, int size, long totalElements,
        String sort, String direction, String popularityBasis, Instant ratingsSyncedAt) {
    public record Item(GameCardResponse game, LocalDate releaseDate, Boolean earlyAccess,
            List<String> languages, Integer editorialRank, Instant popularityUpdatedAt, Rating rating) { }

    /**
     * {@code RATED}: ortalama ve sayı dolu. {@code UNRATED}: doğrulanmış "henüz puan yok" (averageRating null,
     * ratingCount 0). {@code UNAVAILABLE}: puan verisi henüz alınamadı (averageRating ve ratingCount null; 0 değildir).
     */
    public record Rating(RatingStatus status, BigDecimal averageRating, Long ratingCount) { }
}

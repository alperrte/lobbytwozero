package com.ltz.game.infrastructure.client.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Review'ın {@code GET /internal/v1/games/rating-summaries} yanıtı (sözleşme: docs/api.md → REVIEW). Review yanıtı
 * güvenilmez girdidir; alanlar adaptörde doğrulanır. Yeni alanlar eklenirse kırılmasın diye bilinmeyenler yok sayılır.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReviewRatingPageResponse(List<Item> items, UUID nextAfter) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(UUID gameId, BigDecimal averageRating, Long ratingCount) { }
}

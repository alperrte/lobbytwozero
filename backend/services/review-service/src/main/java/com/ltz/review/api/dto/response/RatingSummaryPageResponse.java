package com.ltz.review.api.dto.response;

import java.util.List;
import java.util.UUID;

/** Puanı olan oyunların gameId artan imleçli sayfası; nextAfter null ise son sayfadır. */
public record RatingSummaryPageResponse(List<GameRatingSummaryResponse> items, UUID nextAfter) {
    public RatingSummaryPageResponse { items = List.copyOf(items); }
}

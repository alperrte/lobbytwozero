package com.ltz.review.api.dto.response;

import java.util.List;

public record RatingSummariesResponse(List<GameRatingSummaryResponse> items) {
    public RatingSummariesResponse { items = List.copyOf(items); }
}

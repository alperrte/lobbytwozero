package com.ltz.review.api.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PersonalRatingsResponse(List<Item> items, String nextCursor, boolean truncated) {
    public record Item(UUID gameId, int rating, Instant updatedAt) { }
}

package com.ltz.review.api.dto.response;

import java.time.Instant;
import java.util.UUID;

public record RatingResponse(UUID gameId, int rating, long version, Instant createdAt, Instant updatedAt) {}

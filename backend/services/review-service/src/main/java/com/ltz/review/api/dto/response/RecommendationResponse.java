package com.ltz.review.api.dto.response;

import java.time.Instant;
import java.util.UUID;

public record RecommendationResponse(UUID gameId, boolean recommended, long version, Instant createdAt, Instant updatedAt) {}

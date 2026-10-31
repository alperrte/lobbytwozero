package com.ltz.review.api.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ReviewResponse(UUID id, UUID gameId, String body, boolean ownedByActor, long version, Instant createdAt, Instant updatedAt) {}

package com.ltz.review.api.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ReviewResponse(UUID id, UUID gameId, String body, UUID authorUserId, boolean ownedByActor, String status, long version, Instant createdAt, Instant updatedAt) {}

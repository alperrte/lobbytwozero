package com.ltz.hardware.api.dto.response;

import com.ltz.hardware.domain.enums.HardwareReviewStatus;
import java.time.Instant;
import java.util.UUID;

public record HardwareReviewResponse(UUID id, UUID productId, String body, Integer rating,
        HardwareReviewStatus status, long version, boolean ownedByActor, Instant createdAt, Instant updatedAt) { }

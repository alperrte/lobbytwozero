package com.ltz.hardware.api.dto.response;

import com.ltz.hardware.domain.enums.HardwareReviewReportReason;
import java.time.Instant;
import java.util.UUID;

public record HardwareReviewReportResponse(UUID id, UUID reviewId, HardwareReviewReportReason reason,
        String details, Instant createdAt, String outboxStatus, String deliveryStatus) { }

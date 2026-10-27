package com.ltz.hardware.domain.event;

import com.ltz.hardware.domain.entity.HardwareReview;
import com.ltz.hardware.domain.enums.HardwareReviewReportReason;
import com.ltz.hardware.domain.enums.HardwareReviewStatus;
import java.time.Instant;
import java.util.UUID;

/** Local v1 proposal; no real consumer delivery contract is implied. */
public record HardwareReviewEvent<T>(UUID eventId, String eventType, int eventVersion,
        Instant occurredAt, String producer, UUID aggregateId, T payload) {
    public record Reported(UUID reportId, UUID reviewId, UUID productId, HardwareReviewReportReason reason) { }
    public record Moderated(UUID ownerId, UUID reviewId, HardwareReviewStatus status) { }

    public static HardwareReviewEvent<Reported> reported(HardwareReview review, UUID reportId, HardwareReviewReportReason reason) {
        return new HardwareReviewEvent<>(UUID.randomUUID(), "ContentReported", 1, Instant.now(), "hardware-service", review.getId(),
                new Reported(reportId, review.getId(), review.getProductId(), reason));
    }
    public static HardwareReviewEvent<Moderated> moderated(HardwareReview review) {
        return new HardwareReviewEvent<>(UUID.randomUUID(), "HardwareReviewModerated", 1, Instant.now(), "hardware-service", review.getId(),
                new Moderated(review.getUserId(), review.getId(), review.getStatus()));
    }
}

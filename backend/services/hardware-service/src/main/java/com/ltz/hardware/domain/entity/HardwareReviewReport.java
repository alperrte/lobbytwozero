package com.ltz.hardware.domain.entity;

import com.ltz.hardware.domain.enums.HardwareReviewReportReason;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(name = "hardware_review_reports")
@Getter
public class HardwareReviewReport {
    @Id private UUID id;
    @Column(name = "review_id", nullable = false, updatable = false) private UUID reviewId;
    @Column(name = "reporter_id", nullable = false, updatable = false) private UUID reporterId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32) private HardwareReviewReportReason reason;
    @Column(updatable = false, length = 500) private String details;
    @Column(name = "outbox_event_id") private UUID outboxEventId;
    @Version private Long version;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected HardwareReviewReport() { }

    public HardwareReviewReport(UUID id, UUID reviewId, UUID reporterId,
            HardwareReviewReportReason reason, String details, UUID outboxEventId) {
        this.id = Objects.requireNonNull(id);
        this.reviewId = Objects.requireNonNull(reviewId);
        this.reporterId = Objects.requireNonNull(reporterId);
        this.reason = Objects.requireNonNull(reason);
        this.details = details == null ? null : HardwareReviewText.clean(details, 500);
        this.outboxEventId = Objects.requireNonNull(outboxEventId);
        createdAt = Instant.now();
    }
}

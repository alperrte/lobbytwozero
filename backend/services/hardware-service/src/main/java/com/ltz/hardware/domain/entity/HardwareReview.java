package com.ltz.hardware.domain.entity;

import com.ltz.hardware.domain.enums.HardwareReviewStatus;
import com.ltz.hardware.domain.enums.HardwareReviewModerationAction;
import com.ltz.hardware.domain.exception.HardwareConflictException;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(name = "hardware_reviews")
@Getter
public class HardwareReview {
    @Id private UUID id;
    @Column(name = "product_id", nullable = false, updatable = false) private UUID productId;
    @Column(name = "user_id", nullable = false, updatable = false) private UUID userId;
    @Column(length = 5000) private String body;
    private Integer rating;
    @Column(name = "body_hash", length = 64) private String bodyHash;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16) private HardwareReviewStatus status;
    @Version private Long version;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "deleted_at") private Instant deletedAt;

    protected HardwareReview() { }

    public HardwareReview(UUID productId, UUID userId, String body, Integer rating) {
        id = UUID.randomUUID();
        this.productId = Objects.requireNonNull(productId);
        this.userId = Objects.requireNonNull(userId);
        status = HardwareReviewStatus.VISIBLE;
        createdAt = Instant.now();
        update(body, rating);
    }

    public void requireVersion(long expected) {
        if (version == null || version != expected) throw new HardwareConflictException();
    }

    public void update(String body, Integer rating) {
        if (status != HardwareReviewStatus.VISIBLE) throw new HardwareConflictException();
        if (rating != null && (rating < 1 || rating > 5)) throw new IllegalArgumentException("Geçersiz puan");
        this.body = HardwareReviewText.clean(body, 5000);
        bodyHash = HardwareReviewText.fingerprint(this.body);
        this.rating = rating;
        touch();
    }

    public void delete() {
        if (status == HardwareReviewStatus.DELETED) throw new HardwareConflictException();
        status = HardwareReviewStatus.DELETED;
        body = null;
        bodyHash = null;
        rating = null;
        touch();
        deletedAt = updatedAt;
    }

    public void moderate(HardwareReviewModerationAction action) {
        Objects.requireNonNull(action);
        var expected = action == HardwareReviewModerationAction.HIDE
                ? HardwareReviewStatus.VISIBLE : HardwareReviewStatus.HIDDEN;
        if (status != expected) throw new HardwareConflictException();
        status = action == HardwareReviewModerationAction.HIDE
                ? HardwareReviewStatus.HIDDEN : HardwareReviewStatus.VISIBLE;
        touch();
    }

    private void touch() {
        var now = Instant.now();
        updatedAt = updatedAt != null && !now.isAfter(updatedAt) ? updatedAt.plusNanos(1000) : now;
    }
}

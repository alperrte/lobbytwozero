package com.ltz.social.domain.entity;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.ltz.social.domain.enums.OutboxStatus;
import com.ltz.social.domain.event.SocialEvent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Teslim edilecek olay kaydı. İş verisiyle aynı transaction'da yazılır. Teslim en az bir kezdir: tüketici
 * {@code eventId} ile tekilleştirir (docs/architecture.md §6.2).
 */
@Entity
@Table(name = "outbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, updatable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, updatable = false, length = 128)
    private String eventType;

    @Column(name = "event_version", nullable = false, updatable = false)
    private int eventVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private OutboxStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    public static OutboxEvent pending(SocialEvent event, String payloadJson, Instant now) {
        OutboxEvent outbox = new OutboxEvent();
        outbox.aggregateType = event.aggregateType();
        outbox.aggregateId = event.aggregateId();
        outbox.eventType = event.eventType();
        outbox.eventVersion = event.eventVersion();
        outbox.payload = payloadJson;
        outbox.occurredAt = now;
        outbox.status = OutboxStatus.PENDING;
        outbox.attempts = 0;
        outbox.nextAttemptAt = now;
        return outbox;
    }

    /** Başka bir relay örneği aynı olayı almasın diye "bir sonraki deneme"yi kira süresi kadar öteler. */
    public void lease(Instant now, Duration leaseDuration) {
        nextAttemptAt = now.plus(leaseDuration);
    }

    public void markSent(Instant now) {
        status = OutboxStatus.SENT;
        sentAt = now;
        nextAttemptAt = null;
    }

    /**
     * Başarısız teslim: deneme sayısı artar. En fazla deneme aşılırsa {@code FAILED} olur (el ile müdahale);
     * aksi halde üstel geri çekilmeyle ({@code baseBackoff * 2^(attempts-1)}, {@code maxBackoff} ile sınırlı) yeniden denenir.
     */
    public void markAttemptFailed(Instant now, int maxAttempts, Duration baseBackoff, Duration maxBackoff) {
        attempts++;
        if (attempts >= maxAttempts) {
            status = OutboxStatus.FAILED;
            nextAttemptAt = null;
            return;
        }
        long multiplier = 1L << Math.min(attempts - 1, 20);
        Duration backoff = baseBackoff.multipliedBy(multiplier);
        nextAttemptAt = now.plus(backoff.compareTo(maxBackoff) > 0 ? maxBackoff : backoff);
    }
}

package com.ltz.messaging.domain.entity;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Teslim edilmeyi bekleyen olay (docs/architecture.md §6.2). İş verisiyle aynı transaction'da {@code PENDING} olarak
 * yazılır; olay kaybı ve hayalet olay olmaz. Relay olayı en az bir kez teslim eder: başarıda {@code SENT}, en fazla
 * deneme aşılınca {@code FAILED} (el ile müdahale) olur; arada üstel geri çekilmeyle yeniden denenir.
 */
@Entity
@Table(name = "outbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    static final String PENDING = "PENDING";
    static final String SENT = "SENT";
    static final String FAILED = "FAILED";

    /** Geri çekilme çarpanının üst sınırı (2^20); taşma olmaz, süre zaten {@code maxBackoff} ile sınırlıdır. */
    private static final int MAX_BACKOFF_SHIFT = 20;

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

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    public static OutboxEvent pending(String aggregateType, UUID aggregateId, String eventType, int eventVersion,
                                      String payloadJson, Instant now) {
        OutboxEvent event = new OutboxEvent();
        event.aggregateType = aggregateType;
        event.aggregateId = aggregateId;
        event.eventType = eventType;
        event.eventVersion = eventVersion;
        event.payload = payloadJson;
        event.occurredAt = now;
        event.status = PENDING;
        event.attempts = 0;
        event.nextAttemptAt = now;
        return event;
    }

    /** Başka bir relay örneği aynı olayı almasın diye bir sonraki denemeyi kira süresi kadar öteler. */
    public void lease(Instant now, Duration leaseDuration) {
        nextAttemptAt = now.plus(leaseDuration);
    }

    public void markSent(Instant now) {
        status = SENT;
        sentAt = now;
        nextAttemptAt = null;
    }

    /**
     * Başarısız teslim: deneme sayısı artar. En fazla deneme aşılırsa {@code FAILED} olur; aksi halde
     * {@code baseBackoff * 2^(attempts-1)} ({@code maxBackoff} ile sınırlı) sonra yeniden denenir.
     */
    public void markAttemptFailed(Instant now, int maxAttempts, Duration baseBackoff, Duration maxBackoff) {
        attempts++;
        if (attempts >= maxAttempts) {
            status = FAILED;
            nextAttemptAt = null;
            return;
        }
        Duration backoff = baseBackoff.multipliedBy(1L << Math.min(attempts - 1, MAX_BACKOFF_SHIFT));
        nextAttemptAt = now.plus(backoff.compareTo(maxBackoff) > 0 ? maxBackoff : backoff);
    }

    public boolean isPending() {
        return PENDING.equals(status);
    }

    public boolean isSent() {
        return SENT.equals(status);
    }

    public boolean isFailed() {
        return FAILED.equals(status);
    }
}

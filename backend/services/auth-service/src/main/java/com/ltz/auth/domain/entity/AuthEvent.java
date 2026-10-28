package com.ltz.auth.domain.entity;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.ltz.auth.domain.enums.AuthEventStatus;
import com.ltz.auth.domain.enums.AuthEventType;

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
 * Bir olayın bir hedef servise teslimi (transactional outbox). Çalışan satırı "kiralar" (nextAttemptAt'i ileri alır);
 * böylece çöken bir işlemci olayı kaybetmez, yalnız kira süresi dolunca tekrar denenir. Hata sonrası üstel geri çekilme
 * uygulanır. Aynı {@code eventId} bütün hedeflerde ortaktır; alıcı bunu tekilleştirme anahtarı olarak kullanır.
 */
@Entity
@Table(name = "auth_event_outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthEvent {

    private static final int MAX_BACKOFF_SHIFT = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false, length = 32)
    private AuthEventType type;

    @Column(name = "payload_value", updatable = false, length = 64)
    private String value;

    @Column(nullable = false, updatable = false, length = 32)
    private String target;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AuthEventStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = 64)
    private String lastError;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    public static AuthEvent pending(UUID eventId, UUID accountId, AuthEventType type, String value, String target,
                                    Instant now) {
        if (type.carriesValue()) {
            Objects.requireNonNull(value, "event value");
        }
        AuthEvent event = new AuthEvent();
        event.eventId = eventId;
        event.accountId = accountId;
        event.type = type;
        event.value = type.carriesValue() ? value : null;
        event.target = target;
        event.status = AuthEventStatus.PENDING;
        event.nextAttemptAt = now;
        event.occurredAt = now;
        event.createdAt = now;
        return event;
    }

    /** Denemeyi başlatır ve satırı kira süresi boyunca başka işlemciye görünmez kılar. */
    public void claim(Instant now, Duration lease) {
        attempts++;
        nextAttemptAt = now.plus(lease);
    }

    public boolean isPending() {
        return status == AuthEventStatus.PENDING;
    }

    public void deliver(Instant now) {
        status = AuthEventStatus.DELIVERED;
        deliveredAt = now;
        lastError = null;
    }

    /** Sonraki deneme: initialBackoff * 2^(deneme-1), en fazla maxBackoff. */
    public void fail(Instant now, String errorCode, Duration initialBackoff, Duration maxBackoff) {
        lastError = errorCode;
        Duration backoff = initialBackoff.multipliedBy(1L << Math.clamp(attempts - 1L, 0, MAX_BACKOFF_SHIFT));
        nextAttemptAt = now.plus(backoff.compareTo(maxBackoff) > 0 ? maxBackoff : backoff);
    }
}

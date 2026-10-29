package com.ltz.auth.domain.entity;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.ltz.auth.domain.enums.DeletionTaskStatus;

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
 * Bir servisteki verinin silinmesi görevi. Çalışan görevi "kiralar" (nextAttemptAt'i ileri alır); böylece çöken
 * bir işlemci görevi kaybetmez, yalnız kira süresi dolunca tekrar denenir. Hata sonrası üstel geri çekilme uygulanır.
 */
@Entity
@Table(name = "account_deletion_tasks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountDeletionTask {

    private static final int MAX_BACKOFF_SHIFT = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "request_id", nullable = false, updatable = false)
    private UUID requestId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(nullable = false, updatable = false, length = 32)
    private String service;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DeletionTaskStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = 64)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    /** {@code dueAt}: bekleme süresi bitene kadar görev işlenmez (ilk deneme zamanı). */
    public static AccountDeletionTask create(UUID requestId, UUID accountId, String service, Instant now,
                                             Instant dueAt) {
        AccountDeletionTask task = new AccountDeletionTask();
        task.requestId = requestId;
        task.accountId = accountId;
        task.service = service;
        task.status = DeletionTaskStatus.PENDING;
        task.nextAttemptAt = dueAt;
        task.createdAt = now;
        return task;
    }

    public boolean isPending() {
        return status == DeletionTaskStatus.PENDING;
    }

    /** Denemeyi başlatır ve görevi kira süresi boyunca başka işlemciye görünmez kılar. */
    public void claim(Instant now, Duration lease) {
        attempts++;
        nextAttemptAt = now.plus(lease);
    }

    public void confirm(Instant now) {
        status = DeletionTaskStatus.CONFIRMED;
        confirmedAt = now;
        lastError = null;
    }

    /** Sonraki deneme: initialBackoff * 2^(deneme-1), en fazla maxBackoff. */
    public void fail(Instant now, String errorCode, Duration initialBackoff, Duration maxBackoff) {
        lastError = errorCode;
        Duration backoff = initialBackoff.multipliedBy(1L << Math.clamp(attempts - 1L, 0, MAX_BACKOFF_SHIFT));
        nextAttemptAt = now.plus(backoff.compareTo(maxBackoff) > 0 ? maxBackoff : backoff);
    }
}

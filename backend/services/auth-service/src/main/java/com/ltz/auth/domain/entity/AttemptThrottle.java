package com.ltz.auth.domain.entity;

import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Kayan pencerede başarısız deneme sayacı ve geçici kilit. Deneme doğrulamadan önce ayrılır;
 * böylece eşzamanlı isteklerle sınır aşılamaz. Başarılı doğrulama sayacı sıfırlar.
 */
@Entity
@Table(name = "attempt_throttles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttemptThrottle {

    @Id
    @Column(name = "key_hash", length = 64)
    private String keyHash;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "window_started_at", nullable = false)
    private Instant windowStartedAt;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean isLockedAt(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    public Duration remainingLock(Instant now) {
        return Duration.between(now, lockedUntil);
    }

    public void reserveAttempt(Instant now, Duration window, int maxFailures, Duration lockDuration) {
        if (lockedUntil != null || !now.isBefore(windowStartedAt.plus(window))) {
            reset(now);
        }
        failedAttempts++;
        if (failedAttempts >= maxFailures) {
            lockedUntil = now.plus(lockDuration);
        }
        updatedAt = now;
    }

    public void reset(Instant now) {
        failedAttempts = 0;
        lockedUntil = null;
        windowStartedAt = now;
        updatedAt = now;
    }
}

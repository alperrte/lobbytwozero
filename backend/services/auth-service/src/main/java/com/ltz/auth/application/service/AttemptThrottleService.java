package com.ltz.auth.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.ltz.auth.domain.entity.AttemptThrottle;
import com.ltz.auth.domain.enums.SecurityEventType;
import com.ltz.auth.domain.exception.AuthErrorCode;
import com.ltz.auth.domain.exception.AuthException;
import com.ltz.auth.infrastructure.config.AttemptProperties;
import com.ltz.auth.infrastructure.persistence.repository.AttemptThrottleRepository;

import lombok.RequiredArgsConstructor;

/**
 * AUTH-13 brute-force sınırı. Anahtar, çağıranın ürettiği HMAC özetidir (ör. giriş kimliği); var olmayan
 * hesaplar da aynı şekilde sayıldığından 429 yanıtı hesap varlığını sızdırmaz.
 */
@Service
@RequiredArgsConstructor
public class AttemptThrottleService {

    private final AttemptThrottleRepository repository;
    private final AttemptProperties properties;
    private final SecurityEventRecorder securityEventRecorder;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /** Doğrulamadan önce bir deneme hakkı ayırır; kilitliyse TOO_MANY_ATTEMPTS fırlatır. */
    public void reserve(String keyHash) {
        Duration remainingLock = transactionTemplate.execute(status -> {
            Instant now = clock.instant();
            repository.insertIfAbsent(keyHash, now);
            AttemptThrottle throttle = repository.findByKeyHashForUpdate(keyHash).orElseThrow();
            if (throttle.isLockedAt(now)) {
                return throttle.remainingLock(now);
            }
            throttle.reserveAttempt(now, properties.window(), properties.maxFailures(), properties.lockDuration());
            return null;
        });
        if (remainingLock != null) {
            securityEventRecorder.record(SecurityEventType.ATTEMPTS_THROTTLED, null);
            throw new AuthException(AuthErrorCode.TOO_MANY_ATTEMPTS, remainingLock);
        }
    }

    public void clear(String keyHash) {
        transactionTemplate.executeWithoutResult(status -> repository.findByKeyHashForUpdate(keyHash)
                .ifPresent(throttle -> throttle.reset(clock.instant())));
    }
}

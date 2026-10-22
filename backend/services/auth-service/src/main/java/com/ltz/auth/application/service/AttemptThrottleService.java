package com.ltz.auth.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.ltz.auth.domain.entity.AttemptThrottle;
import com.ltz.auth.domain.exception.AuthErrorCode;
import com.ltz.auth.domain.exception.AuthException;
import com.ltz.auth.infrastructure.config.AttemptProperties;
import com.ltz.auth.infrastructure.persistence.repository.AttemptThrottleRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AUTH-13 brute-force sınırı. Anahtar, çağıranın ürettiği HMAC özetidir (ör. giriş kimliği); var olmayan
 * hesaplar da aynı şekilde sayıldığından 429 yanıtı hesap varlığını sızdırmaz.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttemptThrottleService {

    private final AttemptThrottleRepository repository;
    private final AttemptProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /** Doğrulamadan önce bir deneme hakkı ayırır; kilitliyse TOO_MANY_ATTEMPTS fırlatır. */
    public void reserve(String keyHash) {
        AtomicBoolean lockEngaged = new AtomicBoolean();
        Duration remainingLock = transactionTemplate.execute(status -> {
            Instant now = clock.instant();
            repository.insertIfAbsent(keyHash, now);
            AttemptThrottle throttle = repository.findByKeyHashForUpdate(keyHash).orElseThrow();
            if (throttle.isLockedAt(now)) {
                return throttle.remainingLock(now);
            }
            throttle.reserveAttempt(now, properties.window(), properties.maxFailures(), properties.lockDuration());
            lockEngaged.set(throttle.isLockedAt(now));
            return null;
        });
        if (lockEngaged.get()) {
            // Kilit başına tek kayıt: engellenen her istek log/olay üretmez (kaynak tüketimini artırmaz). Anahtar HMAC'tir.
            log.warn("Attempt lock engaged");
        }
        if (remainingLock != null) {
            throw new AuthException(AuthErrorCode.TOO_MANY_ATTEMPTS, remainingLock);
        }
    }

    public void clear(String keyHash) {
        transactionTemplate.executeWithoutResult(status -> repository.findByKeyHashForUpdate(keyHash)
                .ifPresent(throttle -> throttle.reset(clock.instant())));
    }
}

package com.ltz.messaging.application.service;

import java.time.Clock;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ltz.messaging.infrastructure.config.ReactionProperties;

/**
 * Kullanıcı başına tepki değişikliği sınırı (MSG-23, SECURITY.md §12): tepki ve SSE sağanağına karşı dakikalık sabit
 * pencere. Tepki satırı yerine yazıldığından ayrı sayaç tutulur (DB'de, tüm örnekler ortak).
 */
@Component
public class ReactionRateLimiter {

    private final FixedWindowRateLimiter limiter;

    public ReactionRateLimiter(ReactionProperties properties, JdbcTemplate jdbc, Clock clock) {
        this.limiter = new FixedWindowRateLimiter("reaction", properties::maxPerMinute, jdbc, clock);
    }

    public boolean tryAcquire(UUID userId) {
        return limiter.tryAcquire(userId);
    }

    @Scheduled(fixedDelay = 60_000)
    public void evictExpired() {
        limiter.evictExpired();
    }
}

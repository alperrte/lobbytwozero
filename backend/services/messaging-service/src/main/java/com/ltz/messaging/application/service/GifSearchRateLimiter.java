package com.ltz.messaging.application.service;

import java.time.Clock;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ltz.messaging.infrastructure.config.GifProperties;

/**
 * Kullanıcı başına GIF arama sınırı (SECURITY.md §12, harici sağlayıcı çağrısı): dakikalık sabit pencere, sayaç DB'de
 * (tüm örnekler ortak). Eski pencereler dakikada bir silinir.
 */
@Component
public class GifSearchRateLimiter {

    private final FixedWindowRateLimiter limiter;

    public GifSearchRateLimiter(GifProperties properties, JdbcTemplate jdbc, Clock clock) {
        this.limiter = new FixedWindowRateLimiter("gif-search", properties::searchesPerMinute, jdbc, clock);
    }

    /** Bu dakikadaki aramayı sayar; sınır aşıldıysa {@code false} döner ve sayılmaz. */
    public boolean tryAcquire(UUID userId) {
        return limiter.tryAcquire(userId);
    }

    @Scheduled(fixedDelay = 60_000)
    public void evictExpired() {
        limiter.evictExpired();
    }
}

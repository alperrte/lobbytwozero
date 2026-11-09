package com.ltz.messaging.application.service;

import java.time.Clock;
import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ltz.messaging.infrastructure.config.GifProperties;

/**
 * Kullanıcı başına GIF arama sınırı (SECURITY.md §12, harici sağlayıcı çağrısı): dakikalık sabit pencere. Arama kalıcı
 * kayıt üretmediğinden sayım bellektedir; birden çok örnekte her örnek kendi sınırını uygular (docs-messaging/TASK-04.md açık noktalar).
 * Eski pencereler dakikada bir silinir, bellek aktif kullanıcı sayısıyla sınırlıdır.
 */
@Component
public class GifSearchRateLimiter {

    private final FixedWindowRateLimiter limiter;

    public GifSearchRateLimiter(GifProperties properties, Clock clock) {
        this.limiter = new FixedWindowRateLimiter(properties::searchesPerMinute, clock);
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

package com.ltz.messaging.application.service;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

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

    private static final long WINDOW_MILLIS = 60_000;

    private record Window(long index, int count) {
    }

    private final Map<UUID, Window> windows = new ConcurrentHashMap<>();
    private final GifProperties properties;
    private final Clock clock;

    public GifSearchRateLimiter(GifProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** Bu dakikadaki aramayı sayar; sınır aşıldıysa {@code false} döner ve sayılmaz. */
    public boolean tryAcquire(UUID userId) {
        long current = currentWindow();
        AtomicBoolean allowed = new AtomicBoolean();
        windows.compute(userId, (key, window) -> {
            int used = window == null || window.index() != current ? 0 : window.count();
            if (used >= properties.searchesPerMinute()) {
                return window;
            }
            allowed.set(true);
            return new Window(current, used + 1);
        });
        return allowed.get();
    }

    @Scheduled(fixedDelay = WINDOW_MILLIS)
    public void evictExpired() {
        long current = currentWindow();
        windows.values().removeIf(window -> window.index() != current);
    }

    private long currentWindow() {
        return clock.millis() / WINDOW_MILLIS;
    }
}

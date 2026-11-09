package com.ltz.messaging.application.service;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntSupplier;

/**
 * Kullanıcı başına dakikalık sabit pencere sayacı (SECURITY.md §12). Sayım bellektedir: kalıcı kayıt üretmeyen işlemler
 * (GIF arama, tepki değişikliği) için; birden çok örnekte her örnek kendi sınırını uygular (açık nokta). Eski pencereler
 * {@link #evictExpired()} ile silinir, bellek aktif kullanıcı sayısıyla sınırlıdır.
 */
public final class FixedWindowRateLimiter {

    private static final long WINDOW_MILLIS = 60_000;

    private record Window(long index, int count) {
    }

    private final Map<UUID, Window> windows = new ConcurrentHashMap<>();
    private final IntSupplier limit;
    private final Clock clock;

    public FixedWindowRateLimiter(IntSupplier limit, Clock clock) {
        this.limit = limit;
        this.clock = clock;
    }

    /** Bu dakikadaki işlemi sayar; sınır aşıldıysa {@code false} döner ve sayılmaz. */
    public boolean tryAcquire(UUID userId) {
        long current = currentWindow();
        AtomicBoolean allowed = new AtomicBoolean();
        windows.compute(userId, (key, window) -> {
            int used = window == null || window.index() != current ? 0 : window.count();
            if (used >= limit.getAsInt()) {
                return window;
            }
            allowed.set(true);
            return new Window(current, used + 1);
        });
        return allowed.get();
    }

    public void evictExpired() {
        long current = currentWindow();
        windows.values().removeIf(window -> window.index() != current);
    }

    private long currentWindow() {
        return clock.millis() / WINDOW_MILLIS;
    }
}

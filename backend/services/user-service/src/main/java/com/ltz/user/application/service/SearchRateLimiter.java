package com.ltz.user.application.service;

import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import com.ltz.user.domain.exception.RateLimitedException;
import com.ltz.user.domain.exception.UserErrorCode;
import com.ltz.user.infrastructure.config.SearchLimitProperties;

/**
 * Oyuncu araması için kullanıcı başına sabit pencereli (dakika) sayaç. Anahtar doğrulanmış token'ın {@code sub}
 * değeridir; IP kullanılmaz. Sayaç bellektedir (yeniden başlatmada sıfırlanır, örnek başına geçerlidir).
 */
@Component
@EnableConfigurationProperties(SearchLimitProperties.class)
public class SearchRateLimiter {

    private static final long WINDOW_MILLIS = 60_000L;
    private static final int PURGE_THRESHOLD = 50_000;

    private record Window(long start, int count) {
    }

    private final ConcurrentHashMap<UUID, Window> windows = new ConcurrentHashMap<>();
    private final int perMinute;
    private final Clock clock;

    public SearchRateLimiter(SearchLimitProperties properties, Clock clock) {
        this.perMinute = properties.perMinute();
        this.clock = clock;
    }

    /** Sınır aşıldıysa {@link RateLimitedException} fırlatır; aşılmadıysa isteği sayar. */
    public void acquire(UUID userId) {
        long now = clock.millis();
        long start = now - (now % WINDOW_MILLIS);
        if (windows.size() > PURGE_THRESHOLD) {
            windows.entrySet().removeIf(entry -> entry.getValue().start() != start);
        }
        Window window = windows.merge(userId, new Window(start, 1),
                (old, fresh) -> old.start() == start ? new Window(start, old.count() + 1) : fresh);
        if (window.count() > perMinute) {
            long retryAfter = Math.max(1, (start + WINDOW_MILLIS - now + 999) / 1000);
            throw new RateLimitedException(UserErrorCode.SEARCH_RATE_LIMITED, retryAfter);
        }
    }
}

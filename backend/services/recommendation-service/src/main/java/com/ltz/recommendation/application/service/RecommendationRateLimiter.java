package com.ltz.recommendation.application.service;

import com.ltz.recommendation.domain.exception.RecommendationRequestException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Tek instance için bounded sabit pencere; dağıtık global limit iddiası yoktur. */
@Component
public class RecommendationRateLimiter {
    private final Map<UUID, Window> windows = new HashMap<>();
    private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired
    public RecommendationRateLimiter() { this(Clock.systemUTC()); }
    RecommendationRateLimiter(Clock clock) { this.clock = clock; }

    public synchronized void acquire(UUID account, boolean generation) {
        long minute = clock.instant().getEpochSecond() / 60;
        windows.entrySet().removeIf(entry -> entry.getValue().minute != minute);
        if (!windows.containsKey(account) && windows.size() >= 10000) {
            throw new RecommendationRequestException(503, "RECOMMENDATION_BUSY");
        }
        Window window = windows.computeIfAbsent(account, id -> new Window(minute));
        if (generation ? ++window.generations > 10 : ++window.actions > 120) {
            throw new RecommendationRequestException(429, "RATE_LIMITED");
        }
    }

    public synchronized void erase(UUID account) { windows.remove(account); }
    private static final class Window {
        private final long minute;
        private int generations;
        private int actions;
        private Window(long minute) { this.minute = minute; }
    }
}

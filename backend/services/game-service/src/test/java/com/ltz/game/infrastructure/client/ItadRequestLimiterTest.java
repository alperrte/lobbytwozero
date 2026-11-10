package com.ltz.game.infrastructure.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ltz.game.domain.exception.ProviderException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** ITAD istemci sınırlayıcısı: pencere dolmadan bekletmez, dolunca pencereden çıkışa kadar bekler, çok uzun beklemeyi PRICE_RATE_LIMITED yapar. */
class ItadRequestLimiterTest {
    static class TickClock extends Clock {
        Instant now = Instant.parse("2026-11-10T12:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test void requestsWithinTheLimitNeverWaitAndTheNextOneWaitsUntilTheOldestLeavesTheWindow() {
        TickClock clock = new TickClock();
        List<Long> sleeps = new ArrayList<>();
        var limiter = new ItadRequestLimiter(clock, 3, Duration.ofMinutes(5), Duration.ofMinutes(10), millis -> { sleeps.add(millis); clock.now = clock.now.plusMillis(millis); });
        limiter.acquire();
        clock.now = clock.now.plusSeconds(60);
        limiter.acquire();
        limiter.acquire();
        assertTrue(sleeps.isEmpty(), "sınır içinde bekleme yok");
        limiter.acquire();
        assertEquals(1, sleeps.size());
        assertEquals(Duration.ofMinutes(4).toMillis() + 1, sleeps.getFirst(), "ilk isteğin pencereden çıkmasına kadar");
    }

    @Test void aWaitLongerThanTheMaximumIsReportedAsRateLimitedWithTheRemainingTime() {
        TickClock clock = new TickClock();
        var limiter = new ItadRequestLimiter(clock, 1, Duration.ofMinutes(5), Duration.ofSeconds(30), millis -> clock.now = clock.now.plusMillis(millis));
        limiter.acquire();
        ProviderException error = assertThrows(ProviderException.class, limiter::acquire);
        assertEquals("PRICE_RATE_LIMITED", error.code());
        assertEquals(Duration.ofMinutes(5), error.retryAfter());
    }

    @Test void windowSlidesSoOldRequestsStopCounting() {
        TickClock clock = new TickClock();
        var limiter = new ItadRequestLimiter(clock, 2, Duration.ofMinutes(5), Duration.ZERO, millis -> { });
        limiter.acquire();
        limiter.acquire();
        assertThrows(ProviderException.class, limiter::acquire);
        clock.now = clock.now.plus(Duration.ofMinutes(5));
        limiter.acquire();
    }

    @Test void invalidConfigurationIsRejected() {
        TickClock clock = new TickClock();
        assertThrows(IllegalArgumentException.class, () -> new ItadRequestLimiter(clock, 0, Duration.ofMinutes(5), Duration.ZERO, millis -> { }));
        assertThrows(IllegalArgumentException.class, () -> new ItadRequestLimiter(clock, 1, Duration.ZERO, Duration.ZERO, millis -> { }));
        assertThrows(IllegalArgumentException.class, () -> new ItadRequestLimiter(clock, 1, Duration.ofMinutes(1), Duration.ofSeconds(-1), millis -> { }));
    }
}

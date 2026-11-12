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

    @Test void aWaitLongerThanTheMaximumIsReportedAsALocalThrottleWithTheRemainingTime() {
        TickClock clock = new TickClock();
        var limiter = new ItadRequestLimiter(clock, 1, Duration.ofMinutes(5), Duration.ofSeconds(30), millis -> clock.now = clock.now.plusMillis(millis));
        limiter.acquire();
        ProviderException error = assertThrows(ProviderException.class, limiter::acquire);
        assertEquals(ItadRequestLimiter.LOCAL_THROTTLE, error.code());
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

    @Test void backgroundRequestsStopAtTheLimitMinusTheReserveWhileInteractiveOnesKeepGoingUntilTheFullLimit() {
        TickClock clock = new TickClock();
        var limiter = new ItadRequestLimiter(clock, 5, 2, Duration.ofMinutes(5), Duration.ZERO, millis -> { });
        for (int i = 0; i < 3; i++) limiter.acquire();
        ProviderException background = assertThrows(ProviderException.class, limiter::acquire);
        assertEquals(ItadRequestLimiter.LOCAL_THROTTLE, background.code(), "toplu tur 5-2=3 istekte durur");
        limiter.acquire(true);
        limiter.acquire(true);
        ProviderException interactive = assertThrows(ProviderException.class, () -> limiter.acquire(true));
        assertEquals(ItadRequestLimiter.LOCAL_THROTTLE, interactive.code(), "etkileşimli istek de toplam sınırı (5) aşamaz");
    }

    @Test void theReservedShareStaysAvailableToInteractiveRequestsEvenWhenTheBackgroundShareIsExhausted() {
        TickClock clock = new TickClock();
        var limiter = new ItadRequestLimiter(clock, 4, 1, Duration.ofMinutes(5), Duration.ZERO, millis -> { });
        for (int i = 0; i < 3; i++) limiter.acquire();
        assertThrows(ProviderException.class, limiter::acquire);
        limiter.acquire(true);
    }

    @Test void aBackgroundWaitIsMeasuredAgainstTheRequestThatFreesABackgroundSlotNotTheOldestOne() {
        TickClock clock = new TickClock();
        var limiter = new ItadRequestLimiter(clock, 4, 2, Duration.ofMinutes(5), Duration.ZERO, millis -> { });
        limiter.acquire();
        clock.now = clock.now.plusSeconds(60);
        limiter.acquire();
        limiter.acquire(true);
        limiter.acquire(true);
        ProviderException background = assertThrows(ProviderException.class, limiter::acquire);
        assertEquals(Duration.ofMinutes(5), background.retryAfter(), "arka plan, ilk istek (4 dk) değil 3. en eski istek (5 dk) pencereden çıkınca devam eder");
    }

    @Test void theReserveMustBeSmallerThanTheLimit() {
        TickClock clock = new TickClock();
        assertThrows(IllegalArgumentException.class, () -> new ItadRequestLimiter(clock, 3, 3, Duration.ofMinutes(5), Duration.ZERO, millis -> { }));
        assertThrows(IllegalArgumentException.class, () -> new ItadRequestLimiter(clock, 3, -1, Duration.ofMinutes(5), Duration.ZERO, millis -> { }));
        new ItadRequestLimiter(clock, 3, 2, Duration.ofMinutes(5), Duration.ZERO, millis -> { });
    }

    @Test void invalidConfigurationIsRejected() {
        TickClock clock = new TickClock();
        assertThrows(IllegalArgumentException.class, () -> new ItadRequestLimiter(clock, 0, Duration.ofMinutes(5), Duration.ZERO, millis -> { }));
        assertThrows(IllegalArgumentException.class, () -> new ItadRequestLimiter(clock, 1, Duration.ZERO, Duration.ZERO, millis -> { }));
        assertThrows(IllegalArgumentException.class, () -> new ItadRequestLimiter(clock, 1, Duration.ofMinutes(1), Duration.ofSeconds(-1), millis -> { }));
    }
}

package com.ltz.game.infrastructure.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ltz.game.domain.exception.ProviderException;
import com.ltz.game.infrastructure.config.ItadRateProperties;
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

    @Test void theSnapshotCountsRequestsInTheWindowTheLastMinuteAndTheLastTenSecondsAndIgnoresExpiredOnes() {
        TickClock clock = new TickClock();
        var limiter = new ItadRequestLimiter(clock, 50, 5, Duration.ofMinutes(5), Duration.ZERO, millis -> { });
        for (int i = 0; i < 3; i++) limiter.acquire();
        clock.now = clock.now.plusSeconds(120);
        limiter.acquire();
        limiter.acquire(true);
        clock.now = clock.now.plusSeconds(55);
        limiter.acquire();
        var snapshot = limiter.snapshot();
        assertEquals(6, snapshot.inWindow());
        assertEquals(3, snapshot.last60s(), "son 60 sn: 55 sn önceki iki istek ve şimdiki");
        assertEquals(1, snapshot.last10s());
        assertEquals(50, snapshot.limit());
        assertEquals(5, snapshot.reserve());
        clock.now = clock.now.plus(Duration.ofMinutes(5));
        assertEquals(0, limiter.snapshot().inWindow(), "pencereden çıkan istekler sayılmaz");
    }

    private static ItadRequestLimiter adaptive(TickClock clock, int limit, int reserve, int floor) {
        return ItadRequestLimiter.from(new ItadRateProperties(limit, reserve, Duration.ofMinutes(5), Duration.ZERO, Duration.ZERO, true, floor, Duration.ofMinutes(2)), clock);
    }

    /** Etkin limit kadar istek atılmışken 429 gelmiş gibi: etkin limit %70'ine iner. */
    private static int rateLimitedAtFullWindow(ItadRequestLimiter limiter) { return limiter.onRateLimited(limiter.snapshot().effective()); }

    @Test void a429LowersTheEffectiveLimitToSeventyPercentOfTheObservedRequestsButNeverBelowTheFloor() {
        TickClock clock = new TickClock();
        var limiter = adaptive(clock, 100, 20, 50);
        for (int i = 0; i < 90; i++) limiter.acquire(true);
        assertEquals(63, limiter.onRateLimited(limiter.snapshot().inWindow()), "90 istek x 0,7");
        assertEquals(63, limiter.snapshot().effective());
        assertEquals(50, limiter.onRateLimited(63), "%70 = 44 taban altında: tabanda kalır");
        assertEquals(50, limiter.snapshot().effective());
        assertEquals(100, limiter.snapshot().limit(), "yapılandırılmış limit değişmez");
    }

    @Test void a429ThatArrivesWhileOurOwnTrafficIsSmallDoesNotLowerTheLimitBecauseItComesFromABurstOrAnotherConsumer() {
        TickClock clock = new TickClock();
        var limiter = adaptive(clock, 150, 30, 40);
        assertEquals(150, limiter.onRateLimited(4), "restart sonrası 4 istekte gelen 429");
        assertEquals(150, limiter.onRateLimited(74), "etkin limitin yarısından azı");
        assertEquals(150, limiter.snapshot().effective());
        assertEquals(52, limiter.onRateLimited(75), "yarısına ulaşınca %70'ine iner");
        assertEquals(52, limiter.onRateLimited(1), "düşmüş limitte de küçük gözlem yok sayılır");
        assertEquals(40, limiter.onRateLimited(52), "%70 = 36: tabanda kalır");
    }

    @Test void simultaneous429sFromTheSameObservationDoNotCompound() {
        TickClock clock = new TickClock();
        var limiter = adaptive(clock, 200, 40, 30);
        for (int i = 0; i < 100; i++) limiter.acquire(true);
        int observed = limiter.snapshot().inWindow();
        assertEquals(70, limiter.onRateLimited(observed));
        assertEquals(70, limiter.onRateLimited(observed));
        assertEquals(70, limiter.onRateLimited(observed));
    }

    @Test void theEffectiveLimitGrowsBackByOneTenthEveryIncreaseIntervalWithoutA429AndNeverExceedsTheLimit() {
        TickClock clock = new TickClock();
        var limiter = adaptive(clock, 100, 20, 30);
        assertEquals(70, rateLimitedAtFullWindow(limiter));
        assertEquals(70, limiter.snapshot().effective());
        limiter.acquire(true);
        assertEquals(70, limiter.snapshot().effective(), "aralık dolmadan artmaz");
        clock.now = clock.now.plusSeconds(119);
        limiter.acquire(true);
        assertEquals(70, limiter.snapshot().effective());
        clock.now = clock.now.plusSeconds(2);
        limiter.acquire(true);
        assertEquals(80, limiter.snapshot().effective(), "2 dk sonra +limit/10");
        clock.now = clock.now.plusSeconds(120);
        limiter.acquire(true);
        assertEquals(90, limiter.snapshot().effective());
        for (int i = 0; i < 20; i++) { clock.now = clock.now.plusSeconds(120); limiter.acquire(true); }
        assertEquals(100, limiter.snapshot().effective(), "yapılandırılmış limiti aşmaz");
    }

    @Test void aNew429AfterGrowthRestartsTheIncreaseTimer() {
        TickClock clock = new TickClock();
        var limiter = adaptive(clock, 100, 20, 30);
        assertEquals(70, rateLimitedAtFullWindow(limiter));
        clock.now = clock.now.plusSeconds(121);
        limiter.acquire(true);
        assertEquals(80, limiter.snapshot().effective());
        assertEquals(56, rateLimitedAtFullWindow(limiter));
        clock.now = clock.now.plusSeconds(100);
        limiter.acquire(true);
        assertEquals(56, limiter.snapshot().effective(), "429'dan sonra yeniden tam aralık beklenir");
    }

    @Test void theReservedShareShrinksTogetherWithTheEffectiveLimitSoBackgroundWorkStillGetsRoom() {
        TickClock clock = new TickClock();
        var limiter = adaptive(clock, 100, 40, 30);
        assertEquals(35, limiter.onRateLimited(50), "50 gözlenen istek x 0,7");
        for (int i = 0; i < 21; i++) limiter.acquire();
        assertThrows(ProviderException.class, limiter::acquire);
        for (int i = 0; i < 14; i++) limiter.acquire(true);
        assertThrows(ProviderException.class, () -> limiter.acquire(true));
    }

    @Test void interactiveRequestsGiveUpSoonerThanBackgroundOnesWhenTheWindowIsFull() {
        TickClock clock = new TickClock();
        var rate = new ItadRateProperties(2, 0, Duration.ofSeconds(30), Duration.ofSeconds(30), Duration.ofSeconds(5), false, 2, Duration.ofMinutes(2));
        var limiter = ItadRequestLimiter.from(rate, clock);
        limiter.acquire();
        limiter.acquire();
        ProviderException interactive = assertThrows(ProviderException.class, () -> limiter.acquire(true));
        assertEquals(ItadRequestLimiter.LOCAL_THROTTLE, interactive.code(), "etkileşimli 5 sn'den uzun beklemez");
        assertEquals(Duration.ofSeconds(30), interactive.retryAfter());
    }

    @Test void aNonAdaptiveLimiterKeepsItsLimitOn429() {
        TickClock clock = new TickClock();
        var limiter = new ItadRequestLimiter(clock, 50, 10, Duration.ofMinutes(5), Duration.ZERO, millis -> { });
        for (int i = 0; i < 40; i++) limiter.acquire(true);
        assertEquals(50, limiter.onRateLimited(40));
        assertEquals(50, limiter.snapshot().effective());
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

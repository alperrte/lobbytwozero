package com.ltz.game.application.service;

import com.ltz.game.application.port.GamePriceProvider.Market;
import com.ltz.game.infrastructure.config.GameRefreshProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sayfası açılan oyunların ipucu kuyruğu: sınırlı, tekrar penceresi içinde mükerrer değil, kapalıyken sessiz; asla istisna fırlatmaz.
 */
class RefreshHintsTest {
    static final Market US = new Market("US", "USD"), GB = new Market("GB", "GBP");

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-08T10:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private static GameRefreshProperties properties(boolean enabled, int maxKeys) {
        return new GameRefreshProperties(null, null, 90, 30, null, new GameRefreshProperties.Hints(enabled, maxKeys, Duration.ofMinutes(5), 100, 6));
    }

    @Test void aTouchedGameIsQueuedOnceWithinTheRepeatWindowAndAgainAfterItEvenForTheSameKey() {
        var clock = new MutableClock();
        var hints = new RefreshHints(properties(true, 100), clock);
        UUID game = UUID.randomUUID();
        hints.touch(game, null);
        hints.touch(game, null);
        assertEquals(1, hints.size(), "aynı oyun tekrar penceresi içinde mükerrer kuyruğa girmez");
        assertEquals(List.of(new RefreshHints.Key(game, null)), hints.drain(10));
        hints.touch(game, null);
        assertEquals(0, hints.size(), "pencere dolmadan kuyruktan alınmış oyun yeniden eklenmez");
        clock.now = clock.now.plus(Duration.ofMinutes(6));
        hints.touch(game, null);
        assertEquals(1, hints.size(), "pencere dolunca yeniden eklenir");
    }

    @Test void aRequeuedHintWaitsForItsDelayWithinSaneBoundsAndIsRetriedAtMostTwice() {
        var clock = new MutableClock();
        var hints = new RefreshHints(properties(true, 100), clock);
        UUID game = UUID.randomUUID();
        var key = new RefreshHints.Key(game, US);
        hints.touch(game, US);
        assertEquals(List.of(key), hints.drain(10));
        assertTrue(hints.requeue(key, Duration.ofSeconds(60)));
        assertEquals(1, hints.size());
        assertEquals(List.of(), hints.drain(10), "gecikme dolmadan alınmaz");
        clock.now = clock.now.plusSeconds(61);
        assertEquals(List.of(key), hints.drain(10));
        assertTrue(hints.requeue(key, null), "gecikme bilinmiyorsa en az 10 sn");
        clock.now = clock.now.plusSeconds(9);
        assertEquals(List.of(), hints.drain(10));
        clock.now = clock.now.plusSeconds(2);
        assertEquals(List.of(key), hints.drain(10));
        assertEquals(false, hints.requeue(key, Duration.ofSeconds(60)), "üçüncü yeniden kuyruğa alma reddedilir");
        assertEquals(0, hints.size());
        hints.touch(game, US);
        assertEquals(1, hints.size(), "bırakılan ipucunun görüldü kaydı silinir");
    }

    @Test void aRequeueDelayIsCappedAtFiveMinutesAndANewTouchResetsTheRetryCount() {
        var clock = new MutableClock();
        var hints = new RefreshHints(properties(true, 100), clock);
        UUID game = UUID.randomUUID();
        var key = new RefreshHints.Key(game, GB);
        hints.touch(game, GB);
        hints.drain(10);
        assertTrue(hints.requeue(key, Duration.ofHours(3)));
        clock.now = clock.now.plus(Duration.ofMinutes(5)).plusSeconds(1);
        assertEquals(List.of(key), hints.drain(10), "en çok 5 dk beklenir");
        assertTrue(hints.requeue(key, Duration.ofSeconds(10)));
        clock.now = clock.now.plusSeconds(11);
        hints.drain(10);
        clock.now = clock.now.plus(Duration.ofMinutes(6));
        hints.touch(game, GB);
        hints.drain(10);
        assertTrue(hints.requeue(key, Duration.ofSeconds(10)), "yeni dokunma sayacı sıfırlar");
    }

    @Test void requeueNeverThrowsAndRespectsTheQueueBoundAndTheDisabledSwitch() {
        var full = new RefreshHints(properties(true, 1), new MutableClock());
        full.touch(UUID.randomUUID(), US);
        assertEquals(false, full.requeue(new RefreshHints.Key(UUID.randomUUID(), US), Duration.ofSeconds(10)), "kuyruk doluyken reddedilir");
        assertEquals(false, full.requeue(null, Duration.ofSeconds(10)));
        var off = new RefreshHints(properties(false, 10), new MutableClock());
        assertEquals(false, off.requeue(new RefreshHints.Key(UUID.randomUUID(), US), Duration.ofSeconds(10)));
    }

    @Test void aGamesPriceMarketsAreSeparateHintsFromItsPageView() {
        var hints = new RefreshHints(properties(true, 100), new MutableClock());
        UUID game = UUID.randomUUID();
        hints.touch(game, null);
        hints.touch(game, US);
        hints.touch(game, GB);
        assertEquals(Set.of(new RefreshHints.Key(game, null), new RefreshHints.Key(game, US), new RefreshHints.Key(game, GB)), Set.copyOf(hints.drain(10)));
    }

    @Test void theQueueIsBoundedAndDropsNewHintsWhenFullWithoutFailing() {
        var hints = new RefreshHints(properties(true, 3), new MutableClock());
        for (int index = 0; index < 10; index++) hints.touch(UUID.randomUUID(), null);
        assertEquals(3, hints.size());
        assertEquals(2, hints.drain(2).size(), "tahliye en çok istenen sayıda alır");
        assertEquals(1, hints.size());
    }

    @Test void aDisabledQueueAndNullGamesAreIgnoredAndNothingEverThrows() {
        var disabled = new RefreshHints(properties(false, 100), new MutableClock());
        disabled.touch(UUID.randomUUID(), null);
        assertEquals(0, disabled.size());
        var enabled = new RefreshHints(properties(true, 100), new MutableClock());
        enabled.touch(null, null);
        enabled.touch(null, US);
        assertEquals(0, enabled.size());
        assertTrue(enabled.drain(10).isEmpty());
    }

    @Test void theHintSettingsAreValidated() {
        assertThrows(IllegalArgumentException.class, () -> properties(true, 0));
        assertThrows(IllegalArgumentException.class, () -> new GameRefreshProperties(null, null, 90, 30, null,
                new GameRefreshProperties.Hints(true, 10, Duration.ofSeconds(1), 100, 6)));
        assertThrows(IllegalArgumentException.class, () -> new GameRefreshProperties(null, null, 90, 30, null,
                new GameRefreshProperties.Hints(true, 10, Duration.ofMinutes(5), 101, 6)));
    }
}

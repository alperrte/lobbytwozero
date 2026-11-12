package com.ltz.game.application.service;

import com.ltz.game.application.port.GamePriceProvider.Market;
import com.ltz.game.application.port.GameProvider;
import com.ltz.game.infrastructure.config.GameRefreshProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** İpucu tahliyesi: sayfa açılışı çıkış durumu + detayı, fiyat ipucu o pazarın fiyatını yeniler; dakikadaki tahliye sayısı sınırlıdır. */
class RefreshHintDrainTest {
    static final Market US = new Market("US", "USD"), GB = new Market("GB", "GBP");

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-08T10:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    final MutableClock clock = new MutableClock();
    final GameRefreshProperties properties = new GameRefreshProperties(null, null, 90, 30, null, new GameRefreshProperties.Hints(true, 100, Duration.ofMinutes(5), 100, 2));
    final RefreshHints hints = new RefreshHints(properties, clock);
    final SteamReleaseStatusService status = mock(SteamReleaseStatusService.class);
    final GameDetailBulkService details = mock(GameDetailBulkService.class);
    final GamePriceService prices = mock(GamePriceService.class);
    final GameProvider provider = mock(GameProvider.class);

    private RefreshHintDrain drain(GameRefreshProperties settings) {
        when(provider.storePricesEnabled()).thenReturn(true);
        return new RefreshHintDrain(hints, status, details, prices, provider, settings, clock);
    }

    @Test void pageViewsRefreshStatusAndDetailsAndPriceHintsRefreshTheirOwnMarketOnly() {
        var drain = drain(properties);
        UUID viewed = UUID.randomUUID(), pricedUs = UUID.randomUUID(), pricedGb = UUID.randomUUID();
        hints.touch(viewed, null);
        hints.touch(pricedUs, US);
        hints.touch(pricedGb, GB);
        assertEquals(3, drain.drain());
        verify(status).refreshNow(Set.of(viewed));
        verify(details).refreshNow(Set.of(viewed));
        verify(prices).refreshNowResult(Set.of(pricedUs), US);
        verify(prices).refreshNowResult(Set.of(pricedGb), GB);
        assertEquals(0, hints.size());
    }

    @Test void steamStatusIsSkippedWhenSteamIsNotUsableButDetailsStillRun() {
        var drain = drain(properties);
        when(provider.storePricesEnabled()).thenReturn(false);
        UUID viewed = UUID.randomUUID();
        hints.touch(viewed, null);
        drain.drain();
        verify(status, never()).refreshNow(any());
        verify(details).refreshNow(Set.of(viewed));
    }

    @Test void drainsPerMinuteAreCappedAndTheCapResetsAfterAMinute() {
        // Tahliye başına 1 oyun, dakikada en çok 2 tahliye.
        var small = new GameRefreshProperties(null, null, 90, 30, null, new GameRefreshProperties.Hints(true, 100, Duration.ofMinutes(5), 1, 2));
        var queue = new RefreshHints(small, clock);
        var drain = new RefreshHintDrain(queue, status, details, prices, provider, small, clock);
        when(provider.storePricesEnabled()).thenReturn(true);
        for (int round = 0; round < 3; round++) queue.touch(UUID.randomUUID(), null);
        assertEquals(1, drain.drain());
        assertEquals(1, drain.drain());
        assertEquals(0, drain.drain(), "dakikada en çok 2 tahliye");
        assertEquals(1, queue.size(), "sınır dolunca ipucu kaybolmaz, sonraki tahliyeyi bekler");
        clock.now = clock.now.plusSeconds(61);
        assertEquals(1, drain.drain());
        verify(details, times(3)).refreshNow(any());
    }

    @Test void anEmptyQueueOrADisabledDrainDoesNothing() {
        var drain = drain(properties);
        assertEquals(0, drain.drain());
        var off = new GameRefreshProperties(null, null, 90, 30, null, new GameRefreshProperties.Hints(false, 100, Duration.ofMinutes(5), 100, 2));
        hints.touch(UUID.randomUUID(), null);
        assertEquals(0, drain(off).drain());
        verify(prices, never()).refreshNowResult(any(), eq(US));
    }

    @Test void aPriceHintThatCouldNotRunForATemporaryReasonIsRequeuedAndRunsAgainAfterTheDelay() {
        var drain = drain(properties);
        UUID game = UUID.randomUUID();
        when(prices.refreshNowResult(Set.of(game), US)).thenReturn(RefreshNowResult.retry(Duration.ofSeconds(60)), RefreshNowResult.done(1));
        hints.touch(game, US);
        assertEquals(1, drain.drain());
        assertEquals(1, hints.size(), "geçici neden: ipucu kaybolmaz");
        clock.now = clock.now.plusSeconds(59);
        assertEquals(0, drain.drain(), "gecikme dolmadan tahliye edilmez");
        clock.now = clock.now.plusSeconds(2);
        assertEquals(1, drain.drain());
        assertEquals(0, hints.size(), "ikinci denemede yenilendi");
        verify(prices, times(2)).refreshNowResult(Set.of(game), US);
    }

    @Test void aPermanentFailureOrANullResultIsNotRequeued() {
        var drain = drain(properties);
        UUID permanent = UUID.randomUUID(), unknown = UUID.randomUUID();
        when(prices.refreshNowResult(Set.of(permanent), US)).thenReturn(RefreshNowResult.NOT_REFRESHED);
        hints.touch(permanent, US);
        hints.touch(unknown, GB);
        assertEquals(2, drain.drain());
        assertEquals(0, hints.size());
    }

    @Test void oneMarketsFailureDoesNotLoseTheOtherMarketsHints() {
        var drain = drain(properties);
        UUID us = UUID.randomUUID(), gb = UUID.randomUUID();
        when(prices.refreshNowResult(Set.of(us), US)).thenThrow(new IllegalStateException("beklenmeyen"));
        when(prices.refreshNowResult(Set.of(gb), GB)).thenReturn(RefreshNowResult.done(1));
        hints.touch(us, US);
        hints.touch(gb, GB);
        assertEquals(2, drain.drain());
        verify(prices).refreshNowResult(Set.of(gb), GB);
        assertEquals(0, hints.size(), "beklenmeyen hatada ipucu yeniden kuyruğa alınmaz (kalıcı sayılır)");
    }

    @Test void aTemporaryFailureIsRetriedAtMostTwiceThenDroppedSoALaterViewCanHintAgain() {
        var drain = drain(properties);
        UUID game = UUID.randomUUID();
        when(prices.refreshNowResult(Set.of(game), US)).thenReturn(RefreshNowResult.retry(Duration.ofSeconds(10)));
        hints.touch(game, US);
        for (int attempt = 0; attempt < 3; attempt++) {
            assertEquals(1, drain.drain(), "deneme " + attempt);
            clock.now = clock.now.plusSeconds(61);
        }
        assertEquals(0, hints.size(), "iki yeniden denemeden sonra bırakılır");
        hints.touch(game, US);
        assertEquals(1, hints.size(), "bırakılınca görüldü kaydı silinir: yeni sayfa açılışı hemen ipucu bırakabilir");
    }
}

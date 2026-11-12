package com.ltz.game.application.service;

import com.ltz.game.application.port.GamePriceProvider.Market;
import com.ltz.game.infrastructure.config.PriceDemandProperties;
import com.ltz.game.infrastructure.persistence.repository.GamePriceRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Arka plan pazarları: sabit pazarlar her zaman, diğerleri son günlerde yeterince okunmuşsa ve en çok üç tane; okumalar bellekte birikir, turda veritabanına boşalır. */
class PriceMarketDemandTest {
    static final Market US = new Market("US", "USD"), TR = new Market("TR", "TRY"), DE = new Market("DE", "EUR"), GB = new Market("GB", "GBP"),
            JP = new Market("JP", "JPY"), BR = new Market("BR", "BRL"), KR = new Market("KR", "KRW");
    static final List<Market> CONFIGURED = List.of(US, GB, DE, BR, JP, KR, TR);

    static final class TickClock extends Clock {
        Instant now = Instant.parse("2026-11-12T10:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    final GamePriceRepository repository = mock(GamePriceRepository.class);
    final TickClock clock = new TickClock();

    private PriceMarketDemand demand(PriceDemandProperties properties) { return new PriceMarketDemand(repository, properties, clock); }

    @Test void withoutDemandOnlyTheAlwaysMarketsAreActiveInConfiguredOrder() {
        when(repository.demandMarkets(any(), anyInt())).thenReturn(List.of());
        assertEquals(List.of(US, TR), demand(PriceDemandProperties.defaults()).activeMarkets(CONFIGURED));
    }

    @Test void marketsReadOftenEnoughAreAddedMostReadFirstButAtMostMaxSecondaryAndNeverTwice() {
        when(repository.demandMarkets(any(), anyInt())).thenReturn(List.of(JP, US, DE, KR, BR));
        var active = demand(new PriceDemandProperties(true, "US:USD,TR:TRY", Duration.ofDays(7), 3, 3)).activeMarkets(CONFIGURED);
        assertEquals(List.of(US, DE, JP, KR, TR), active, "en çok okunan üç ikincil JP, DE, KR (BR dördüncü); sonuç yapılandırma sırasında");
    }

    @Test void theDemandWindowAndThresholdComeFromTheProperties() {
        when(repository.demandMarkets(any(), anyInt())).thenReturn(List.of());
        demand(new PriceDemandProperties(true, "US:USD", Duration.ofDays(5), 4, 2)).activeMarkets(CONFIGURED);
        verify(repository).demandMarkets(LocalDate.of(2026, 11, 7), 4);
    }

    @Test void whenDisabledEveryConfiguredMarketIsActiveAndNothingIsCounted() {
        var off = demand(new PriceDemandProperties(false, "US:USD", Duration.ofDays(7), 3, 3));
        off.touch(DE, CONFIGURED, 1);
        assertEquals(CONFIGURED, off.activeMarkets(CONFIGURED));
        verify(repository, never()).recordDemand(any(), any());
        verify(repository, never()).demandMarkets(any(), anyInt());
    }

    @Test void readsAccumulateInMemoryAndAreWrittenOncePerRoundForConfiguredMarketsOnly() {
        when(repository.demandMarkets(any(), anyInt())).thenReturn(List.of());
        var demand = demand(PriceDemandProperties.defaults());
        for (int read = 0; read < 5; read++) demand.touch(DE, CONFIGURED, 1);
        demand.touch(GB, CONFIGURED, 1);
        demand.touch(new Market("AR", "ARS"), CONFIGURED, 1);
        demand.touch(null, CONFIGURED, 1);
        verify(repository, never()).recordDemand(any(), any());
        demand.activeMarkets(CONFIGURED);
        @SuppressWarnings("unchecked") ArgumentCaptor<Map<Market, Long>> written = ArgumentCaptor.forClass(Map.class);
        verify(repository).recordDemand(written.capture(), eq(LocalDate.of(2026, 11, 12)));
        assertEquals(Map.of(DE, 5L, GB, 1L), written.getValue());
        demand.activeMarkets(CONFIGURED);
        verify(repository, times(1)).recordDemand(any(), any());
    }

    @Test void aFailedWriteKeepsTheCountsForTheNextRoundAndFallsBackToTheAlwaysMarkets() {
        var demand = demand(PriceDemandProperties.defaults());
        demand.touch(DE, CONFIGURED, 1);
        demand.touch(DE, CONFIGURED, 1);
        doThrow(new IllegalStateException("db")).doNothing().when(repository).recordDemand(any(), any());
        when(repository.demandMarkets(any(), anyInt())).thenReturn(List.of(DE));
        assertEquals(List.of(US, TR), demand.activeMarkets(CONFIGURED), "sayaç tablosu yazılamadı: kota yememek için yalnız sabit pazarlar");
        assertEquals(List.of(US, DE, TR), demand.activeMarkets(CONFIGURED), "sonraki turda sayılar kaybolmadan yazıldı");
        @SuppressWarnings("unchecked") ArgumentCaptor<Map<Market, Long>> written = ArgumentCaptor.forClass(Map.class);
        verify(repository, times(2)).recordDemand(written.capture(), any());
        assertEquals(Map.of(DE, 2L), written.getAllValues().getLast());
    }

    @Test void aReadThatReturnedNoActiveGameIsNotCountedAndTheDisabledInstanceNeverTouchesTheDatabase() {
        when(repository.demandMarkets(any(), anyInt())).thenReturn(List.of());
        var demand = demand(PriceDemandProperties.defaults());
        demand.touch(DE, CONFIGURED, 0);
        demand.activeMarkets(CONFIGURED);
        verify(repository, never()).recordDemand(any(), any());
        var off = PriceMarketDemand.disabled();
        off.touch(DE, CONFIGURED, 5);
        assertEquals(CONFIGURED, off.activeMarkets(CONFIGURED));
    }

    @Test void oldCounterRowsArePrunedOncePerDay() {
        when(repository.demandMarkets(any(), anyInt())).thenReturn(List.of());
        var demand = demand(PriceDemandProperties.defaults());
        demand.activeMarkets(CONFIGURED);
        demand.activeMarkets(CONFIGURED);
        verify(repository, times(1)).pruneDemand(LocalDate.of(2026, 9, 28));
        clock.now = clock.now.plus(Duration.ofDays(1));
        demand.activeMarkets(CONFIGURED);
        verify(repository, times(1)).pruneDemand(LocalDate.of(2026, 9, 29));
    }
}

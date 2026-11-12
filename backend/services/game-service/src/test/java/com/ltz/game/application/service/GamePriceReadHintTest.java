package com.ltz.game.application.service;

import com.ltz.game.application.port.GamePriceProvider;
import com.ltz.game.application.port.GamePriceProvider.Market;
import com.ltz.game.domain.entity.Game;
import com.ltz.game.infrastructure.config.GamePriceProperties;
import com.ltz.game.infrastructure.config.GameRefreshProperties;
import com.ltz.game.infrastructure.persistence.repository.GamePriceRepository;
import com.ltz.game.infrastructure.persistence.repository.GameRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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

/**
 * Fiyat okuması salt yereldir; oyun sayfasındaki tek oyunluk okuma yalnız bellek içi ipucu bırakır (dış servise gitmez, yazmaz). Liste okumaları
 * (birden çok oyun) ve yapılandırılmamış pazarlar ipucu bırakmaz.
 */
class GamePriceReadHintTest {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-08T10:00:00Z"), ZoneOffset.UTC);
    static final Market US = new Market("US", "USD"), DE = new Market("DE", "EUR");

    final RefreshHints hints = mock(RefreshHints.class);
    final GameRepository games = mock(GameRepository.class);
    final GamePriceRepository repository = mock(GamePriceRepository.class);
    final UUID id = UUID.randomUUID(), other = UUID.randomUUID();

    private GamePriceService service() {
        var properties = new GamePriceProperties("k", "https://api.isthereanydeal.com", "US", "USD", Duration.ofHours(1), "");
        var first = new Game();
        first.setId(id);
        var second = new Game();
        second.setId(other);
        when(games.findAllById(any())).thenReturn(List.of(first, second));
        return new GamePriceService(mock(GamePriceProvider.class), repository, games, properties, CLOCK, null, null, GameRefreshProperties.defaults(), hints);
    }

    @Test void aSingleGameReadLeavesAHintForItsMarketAndStillReturnsOnlyLocalData() {
        var response = service().read(List.of(id), US);
        verify(hints).touch(id, US);
        assertEquals("UNAVAILABLE", response.items().getFirst().status());
        verify(repository, never()).claim(any(), any());
    }

    @Test void everyReadOfActiveGamesCountsAsDemandForItsMarketButReadsOfUnknownGamesDoNot() {
        var service = service();
        var demand = mock(PriceMarketDemand.class);
        service.useDemand(demand);
        UUID unknown = UUID.randomUUID();
        when(games.findAllById(List.of(unknown))).thenReturn(List.of());
        service.read(List.of(id), US);
        service.read(List.of(id, other), US);
        service.read(List.of(unknown), US);
        verify(demand).touch(eq(US), any(), eq(0));
        verify(demand, times(2)).touch(eq(US), any(), eq(2));
        service.read(List.of(id), DE);
        verify(demand).touch(eq(DE), any(), eq(2));
    }

    @Test void listReadsAndUnconfiguredMarketsLeaveNoHint() {
        var service = service();
        service.read(List.of(id, other), US);
        service.read(List.of(id), DE);
        verify(hints, never()).touch(any(), any());
    }
}

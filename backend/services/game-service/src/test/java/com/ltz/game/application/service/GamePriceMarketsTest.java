package com.ltz.game.application.service;

import com.ltz.game.application.port.GamePriceProvider;
import com.ltz.game.application.port.GamePriceProvider.Market;
import com.ltz.game.domain.exception.ProviderException;
import com.ltz.game.infrastructure.config.GamePriceProperties;
import com.ltz.game.infrastructure.persistence.repository.GamePriceRepository;
import com.ltz.game.infrastructure.persistence.repository.GameRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Fiyat yenileme: yapılandırılmış her pazar (US/USD, GB/GBP, DE/EUR) ayrı ayrı yenilenir; TRY sunulmaz. */
class GamePriceMarketsTest {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-08T10:00:00Z"), ZoneOffset.UTC);
    static final String API = "https://api.isthereanydeal.com";

    private static GamePriceProperties properties(String markets) {
        return new GamePriceProperties("fixture-key", API, "US", "USD", Duration.ofMinutes(15), markets);
    }

    @Test void marketsAreParsedValidatedDeduplicatedAndThePrimaryMarketComesFirst() {
        assertEquals(List.of(new Market("US", "USD"), new Market("GB", "GBP"), new Market("DE", "EUR")), properties("US:USD,GB:GBP,DE:EUR").allMarkets());
        assertEquals(List.of(new Market("US", "USD"), new Market("DE", "EUR")), properties(" DE:EUR , US:USD ").allMarkets());
        assertEquals(List.of(new Market("US", "USD")), properties("").allMarkets(), "ek pazar yoksa yalnız birincil pazar");
        assertEquals(List.of(new Market("US", "USD")), new GamePriceProperties("k", API, "US", "USD", Duration.ofMinutes(15)).allMarkets(), "eski tek pazarlı kurucu");
        for (String bad : List.of("US", "US:USD:X", "XX:USD", "US:TRYY", "us:usd:", ":")) {
            assertThrows(IllegalArgumentException.class, () -> properties(bad).allMarkets(), bad);
        }
    }

    @Test void everyMarketIsRefreshedWithItsOwnLeaseCandidatesAndPublish() {
        var provider = mock(GamePriceProvider.class);
        var repository = mock(GamePriceRepository.class);
        UUID game = UUID.randomUUID();
        when(provider.isConfigured()).thenReturn(true);
        when(repository.claim(any(), any())).thenReturn(true);
        when(repository.candidates(any(), any(), any(), any(), any())).thenReturn(List.of(new GamePriceRepository.Candidate(game, Duration.ofHours(2))));
        when(provider.prices(any(), any())).thenReturn(List.of());
        var service = new GamePriceService(provider, repository, mock(GameRepository.class), properties("US:USD,GB:GBP,DE:EUR"), CLOCK);
        service.refresh();
        for (Market market : List.of(new Market("US", "USD"), new Market("GB", "GBP"), new Market("DE", "EUR"))) {
            verify(provider).prices(eq(Set.of(game)), eq(market));
            verify(repository).publish(any(), eq(Set.of(game)), eq(market), any(), any());
        }
        verify(repository, times(3)).release(any());
    }

    @Test void aFailingMarketDoesNotStopTheOthersAndIsRecordedAsFailedForThatMarketOnly() {
        var provider = mock(GamePriceProvider.class);
        var repository = mock(GamePriceRepository.class);
        UUID game = UUID.randomUUID();
        when(provider.isConfigured()).thenReturn(true);
        when(repository.claim(any(), any())).thenReturn(true);
        when(repository.candidates(any(), any(), any(), any(), any())).thenReturn(List.of(new GamePriceRepository.Candidate(game, Duration.ofHours(2))));
        when(provider.prices(any(), eq(new Market("GB", "GBP")))).thenThrow(new ProviderException("RATE_LIMITED"));
        when(provider.prices(any(), eq(new Market("US", "USD")))).thenReturn(List.of());
        when(provider.prices(any(), eq(new Market("DE", "EUR")))).thenReturn(List.of());
        var service = new GamePriceService(provider, repository, mock(GameRepository.class), properties("US:USD,GB:GBP,DE:EUR"), CLOCK);
        service.refresh();
        verify(repository).failed(any(), eq(Set.of(game)), eq(new Market("GB", "GBP")), any(), eq("RATE_LIMITED"), any());
        verify(repository).publish(any(), any(), eq(new Market("US", "USD")), any(), any());
        verify(repository).publish(any(), any(), eq(new Market("DE", "EUR")), any(), any());
        verify(repository, never()).failed(any(), any(), eq(new Market("US", "USD")), any(), any(), any());
        verify(repository, times(3)).release(any());
    }

    @Test void aMarketWhoseLeaseIsHeldElsewhereIsSkippedWithoutCallingTheProvider() {
        var provider = mock(GamePriceProvider.class);
        var repository = mock(GamePriceRepository.class);
        when(provider.isConfigured()).thenReturn(true);
        when(repository.claim(any(), any())).thenReturn(false);
        new GamePriceService(provider, repository, mock(GameRepository.class), properties("US:USD,GB:GBP"), CLOCK).refresh();
        verify(provider, never()).prices(any(), any());
    }

    @Test void nothingRunsWhenThePriceProviderIsNotConfigured() {
        var provider = mock(GamePriceProvider.class);
        var repository = mock(GamePriceRepository.class);
        when(provider.isConfigured()).thenReturn(false);
        new GamePriceService(provider, repository, mock(GameRepository.class), properties("US:USD,GB:GBP"), CLOCK).refresh();
        verify(repository, never()).claim(any(), any());
    }

    @Test void eachGamesQuoteIsShortenedToItsTierFreshnessAndNeverExtended() {
        var provider = mock(GamePriceProvider.class);
        var repository = mock(GamePriceRepository.class);
        UUID hot = UUID.randomUUID(), cold = UUID.randomUUID();
        when(provider.isConfigured()).thenReturn(true);
        when(repository.claim(any(), any())).thenReturn(true);
        when(repository.candidates(any(), any(), any(), any(), any())).thenReturn(List.of(
                new GamePriceRepository.Candidate(hot, Duration.ofHours(2)), new GamePriceRepository.Candidate(cold, Duration.ofHours(48))));
        Instant fetched = CLOCK.instant();
        var market = new Market("US", "USD");
        // Sağlayıcı en uzun tazeliği (48 sa) verir; ayrıca bir teklifin satış bitişi 30 dk sonradır.
        var quotes = List.of(
                new GamePriceProvider.Quote(hot, "Steam", market, new java.math.BigDecimal("10"), new java.math.BigDecimal("10"), "ITAD", fetched, fetched.plus(Duration.ofHours(48)), "https://itad.link/x", 0, fetched.minusSeconds(5)),
                new GamePriceProvider.Quote(cold, "Steam", market, new java.math.BigDecimal("10"), new java.math.BigDecimal("10"), "ITAD", fetched, fetched.plus(Duration.ofHours(48)), "https://itad.link/y", 0, fetched.minusSeconds(5)),
                new GamePriceProvider.Quote(cold, "GOG", market, new java.math.BigDecimal("8"), new java.math.BigDecimal("4"), "ITAD", fetched, fetched.plus(Duration.ofMinutes(30)), "https://itad.link/z", 50, fetched.minusSeconds(5)));
        when(provider.prices(any(), eq(market))).thenReturn(quotes);
        var captured = org.mockito.ArgumentCaptor.forClass(List.class);
        var properties = new GamePriceProperties("fixture-key", API, "US", "USD", Duration.ofHours(2), "");
        new GamePriceService(provider, repository, mock(GameRepository.class), properties, CLOCK).refresh();
        verify(repository).publish(any(), any(), eq(market), captured.capture(), any());
        @SuppressWarnings("unchecked") List<GamePriceProvider.Quote> published = captured.getValue();
        assertEquals(fetched.plus(Duration.ofHours(2)), published.get(0).expiresAt(), "sıcak oyun: 2 saat");
        assertEquals(fetched.plus(Duration.ofHours(48)), published.get(1).expiresAt(), "soğuk oyun: 48 saat");
        assertEquals(fetched.plus(Duration.ofMinutes(30)), published.get(2).expiresAt(), "satış bitişi kademeden önceyse o geçerli (uzatılmaz)");
    }

    @Test void aLargeBacklogIsDrainedInSeveralBatchesPerRunUpToTheCapAndAShortBatchStops() {
        var provider = mock(GamePriceProvider.class);
        var repository = mock(GamePriceRepository.class);
        when(provider.isConfigured()).thenReturn(true);
        when(repository.claim(any(), any())).thenReturn(true);
        when(repository.publish(any(), any(), any(), any(), any())).thenReturn(true);
        when(provider.prices(any(), any())).thenReturn(List.of());
        var full = new java.util.ArrayList<GamePriceRepository.Candidate>();
        for (int index = 0; index < 200; index++) full.add(new GamePriceRepository.Candidate(UUID.randomUUID(), Duration.ofHours(48)));
        when(repository.candidates(any(), any(), any(), any(), any())).thenReturn(full);
        new GamePriceService(provider, repository, mock(GameRepository.class), properties(""), CLOCK).refresh();
        verify(provider, times(GamePriceService.MAX_BATCHES_PER_RUN)).prices(any(), any());
        verify(repository, times(1)).release(any());

        var shortProvider = mock(GamePriceProvider.class);
        var shortRepository = mock(GamePriceRepository.class);
        when(shortProvider.isConfigured()).thenReturn(true);
        when(shortRepository.claim(any(), any())).thenReturn(true);
        when(shortRepository.publish(any(), any(), any(), any(), any())).thenReturn(true);
        when(shortProvider.prices(any(), any())).thenReturn(List.of());
        when(shortRepository.candidates(any(), any(), any(), any(), any())).thenReturn(List.of(new GamePriceRepository.Candidate(UUID.randomUUID(), Duration.ofHours(2))));
        new GamePriceService(shortProvider, shortRepository, mock(GameRepository.class), properties(""), CLOCK).refresh();
        verify(shortProvider, times(1)).prices(any(), any());
    }

    @Test void tiersAreMultiplesOfTheHotFreshnessAndTheTtlHasASafeRange() {
        var properties = new GamePriceProperties("k", API, "US", "USD", Duration.ofHours(2), "");
        assertEquals(Duration.ofHours(12), properties.warmTtl());
        assertEquals(Duration.ofHours(48), properties.coldTtl());
        assertThrows(IllegalArgumentException.class, () -> new GamePriceProperties("k", API, "US", "USD", Duration.ofHours(3), ""));
        assertThrows(IllegalArgumentException.class, () -> new GamePriceProperties("k", API, "US", "USD", Duration.ZERO, ""));
    }
}

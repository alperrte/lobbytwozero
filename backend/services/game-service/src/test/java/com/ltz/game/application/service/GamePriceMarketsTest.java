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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Fiyat yenileme: yapılandırılmış her pazar (US/USD, GB/GBP, DE/EUR, TR/TRY ...) ayrı ayrı yenilenir. */
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

    @Test void theDeliveredConfigurationHasThirteenMarketsIncludingTurkeyWithTurkishLira() throws Exception {
        var file = java.nio.file.Path.of("src/main/resources/application.properties");
        var props = new java.util.Properties();
        try (var reader = java.nio.file.Files.newBufferedReader(file, java.nio.charset.StandardCharsets.UTF_8)) { props.load(reader); }
        var markets = properties(props.getProperty("ltz.game.pricing.markets")).allMarkets();
        assertEquals(13, markets.size());
        assertEquals(new Market("TR", "TRY"), markets.getLast());
        assertEquals(new Market("US", "USD"), markets.getFirst(), "birincil pazar ilk");
        var steam = new com.ltz.game.infrastructure.config.GameRefreshProperties(null, null, 90, 30, null, null).steamMarkets();
        assertEquals(12, steam.size(), "Steam doğrudan fiyat pazarları Türkiye'yi içermez (Steam Türkiye'de ABD doları ile satar)");
        assertEquals(false, steam.contains(new Market("TR", "TRY")));
    }

    @Test void aViewedGameWaitsBrieflyForTheLeaseThatTheScheduledRunHoldsInsteadOfBeingDropped() {
        var provider = mock(GamePriceProvider.class);
        var repository = mock(GamePriceRepository.class);
        UUID game = UUID.randomUUID();
        when(provider.isConfigured()).thenReturn(true);
        when(repository.tiers(any(), any(), any(), any())).thenReturn(java.util.Map.of(game, Duration.ofHours(8)));
        when(repository.claim(any(), any())).thenReturn(false, false, true);
        when(repository.publish(any(), any(), any(), any(), any())).thenReturn(true);
        when(provider.prices(any(), any())).thenReturn(List.of());
        var service = new GamePriceService(provider, repository, mock(GameRepository.class), properties("US:USD"), CLOCK);
        var sleeps = new java.util.ArrayList<Long>();
        service.sleeperForTests(sleeps::add);
        assertEquals(1, service.refreshNow(Set.of(game), new Market("US", "USD")));
        assertEquals(List.of(1000L, 1000L), sleeps, "kiralama iki kez dolu, üçüncüde alındı");
    }

    @Test void aViewedGameAsksTheProviderWithInteractivePriorityWhileTheScheduledRunKeepsBackgroundPriority() {
        var provider = mock(GamePriceProvider.class);
        var repository = mock(GamePriceRepository.class);
        UUID game = UUID.randomUUID();
        when(provider.isConfigured()).thenReturn(true);
        when(repository.tiers(any(), any(), any(), any())).thenReturn(java.util.Map.of(game, Duration.ofHours(8)));
        when(repository.claim(any(), any())).thenReturn(true);
        when(repository.publish(any(), any(), any(), any(), any())).thenReturn(true);
        when(repository.candidates(any(), any(), any(), any(), any(), anyInt())).thenReturn(List.of(new GamePriceRepository.Candidate(game, Duration.ofHours(2))));
        when(provider.prices(any(), any(), any())).thenReturn(List.of());
        when(provider.prices(any(), any())).thenReturn(List.of());
        var service = new GamePriceService(provider, repository, mock(GameRepository.class), properties("US:USD"), CLOCK);
        assertEquals(1, service.refreshNow(Set.of(game), new Market("US", "USD")));
        verify(provider).prices(any(), any(), eq(GamePriceProvider.Priority.INTERACTIVE));
        verify(provider, never()).prices(any(), any());
        service.refresh();
        verify(provider, times(1)).prices(any(), any());
        verify(provider, times(1)).prices(any(), any(), any());
    }

    @Test void aViewedGameGivesUpWhenTheLeaseNeverFrees() {
        var provider = mock(GamePriceProvider.class);
        var repository = mock(GamePriceRepository.class);
        UUID game = UUID.randomUUID();
        when(provider.isConfigured()).thenReturn(true);
        when(repository.tiers(any(), any(), any(), any())).thenReturn(java.util.Map.of(game, Duration.ofHours(8)));
        when(repository.claim(any(), any())).thenReturn(false);
        var service = new GamePriceService(provider, repository, mock(GameRepository.class), properties("US:USD"), CLOCK);
        var sleeps = new java.util.ArrayList<Long>();
        service.sleeperForTests(sleeps::add);
        assertEquals(-1, service.refreshNow(Set.of(game), new Market("US", "USD")));
        assertEquals(GamePriceService.CLAIM_ATTEMPTS - 1, sleeps.size());
        verify(provider, never()).prices(any(), any());
    }

    @Test void everyMarketIsRefreshedWithItsOwnLeaseCandidatesAndPublish() {
        var provider = mock(GamePriceProvider.class);
        var repository = mock(GamePriceRepository.class);
        UUID game = UUID.randomUUID();
        when(provider.isConfigured()).thenReturn(true);
        when(repository.claim(any(), any())).thenReturn(true);
        when(repository.candidates(any(), any(), any(), any(), any(), anyInt())).thenReturn(List.of(new GamePriceRepository.Candidate(game, Duration.ofHours(2))));
        when(provider.prices(any(), any())).thenReturn(List.of());
        var service = new GamePriceService(provider, repository, mock(GameRepository.class), properties("US:USD,GB:GBP,DE:EUR"), CLOCK);
        service.refresh();
        for (Market market : List.of(new Market("US", "USD"), new Market("GB", "GBP"), new Market("DE", "EUR"))) {
            verify(provider).prices(Set.of(game), market);
            verify(repository).publish(any(), eq(Set.of(game)), eq(market), any(), any());
        }
        verify(repository, times(1)).claim(any(), any());
        verify(repository, times(1)).release(any());
    }

    @Test void aFailingMarketDoesNotStopTheOthersAndIsRecordedAsFailedForThatMarketOnly() {
        var provider = mock(GamePriceProvider.class);
        var repository = mock(GamePriceRepository.class);
        UUID game = UUID.randomUUID();
        when(provider.isConfigured()).thenReturn(true);
        when(repository.claim(any(), any())).thenReturn(true);
        when(repository.candidates(any(), any(), any(), any(), any(), anyInt())).thenReturn(List.of(new GamePriceRepository.Candidate(game, Duration.ofHours(2))));
        when(provider.prices(any(), eq(new Market("GB", "GBP")))).thenThrow(new ProviderException("RATE_LIMITED"));
        when(provider.prices(any(), eq(new Market("US", "USD")))).thenReturn(List.of());
        when(provider.prices(any(), eq(new Market("DE", "EUR")))).thenReturn(List.of());
        var service = new GamePriceService(provider, repository, mock(GameRepository.class), properties("US:USD,GB:GBP,DE:EUR"), CLOCK);
        service.refresh();
        verify(repository).failed(any(), eq(Set.of(game)), eq(new Market("GB", "GBP")), any(), eq("RATE_LIMITED"), any());
        verify(repository).publish(any(), any(), eq(new Market("US", "USD")), any(), any());
        verify(repository).publish(any(), any(), eq(new Market("DE", "EUR")), any(), any());
        verify(repository, never()).failed(any(), any(), eq(new Market("US", "USD")), any(), any(), any());
        verify(repository, times(1)).release(any());
    }

    @Test void marketsAreRefreshedInParallelUnderOneLease() {
        var provider = mock(GamePriceProvider.class);
        var repository = mock(GamePriceRepository.class);
        UUID game = UUID.randomUUID();
        when(provider.isConfigured()).thenReturn(true);
        when(repository.claim(any(), any())).thenReturn(true);
        when(repository.publish(any(), any(), any(), any(), any())).thenReturn(true);
        when(repository.candidates(any(), any(), any(), any(), any(), anyInt())).thenReturn(List.of(new GamePriceRepository.Candidate(game, Duration.ofHours(2))));
        // İki pazar aynı anda yanıt beklemedikçe kilit açılmaz: sıralı işlem zaman aşımına uğrar.
        var bothInFlight = new java.util.concurrent.CountDownLatch(2);
        var concurrent = new java.util.concurrent.atomic.AtomicBoolean();
        when(provider.prices(any(), any())).thenAnswer(call -> {
            bothInFlight.countDown();
            if (bothInFlight.await(5, java.util.concurrent.TimeUnit.SECONDS)) concurrent.set(true);
            return List.of();
        });
        new GamePriceService(provider, repository, mock(GameRepository.class), properties("US:USD,GB:GBP,DE:EUR"), CLOCK).refresh();
        assertEquals(true, concurrent.get(), "en az iki pazar aynı anda işlendi");
        verify(repository, times(1)).claim(any(), any());
        verify(repository, times(1)).release(any());
    }

    @Test void aViewedGameDuringAScheduledRunReusesTheRunningLeaseInsteadOfWaitingForIt() {
        var provider = mock(GamePriceProvider.class);
        var repository = mock(GamePriceRepository.class);
        UUID game = UUID.randomUUID();
        when(provider.isConfigured()).thenReturn(true);
        when(repository.claim(any(), any())).thenReturn(true);
        when(repository.publish(any(), any(), any(), any(), any())).thenReturn(true);
        when(repository.tiers(any(), any(), any(), any())).thenReturn(java.util.Map.of(game, Duration.ofHours(8)));
        when(repository.candidates(any(), any(), any(), any(), any(), anyInt())).thenReturn(List.of(new GamePriceRepository.Candidate(game, Duration.ofHours(8))));
        var service = new GamePriceService(provider, repository, mock(GameRepository.class), properties("US:USD"), CLOCK);
        var viewedRefresh = new java.util.concurrent.atomic.AtomicInteger(Integer.MIN_VALUE);
        var entered = new java.util.concurrent.atomic.AtomicBoolean();
        when(provider.prices(any(), any())).thenAnswer(call -> {
            if (entered.compareAndSet(false, true)) viewedRefresh.set(service.refreshNow(Set.of(game), new Market("US", "USD")));
            return List.of();
        });
        service.refresh();
        assertEquals(1, viewedRefresh.get(), "sayfası açılan oyun çalışan turun kiralamasıyla yenilendi");
        verify(repository, times(1)).claim(any(), any());
        verify(repository, times(1)).release(any());
    }

    @Test void aLocalThrottleLeavesTheMarketAloneWithoutMarkingAnyGameAsFailed() {
        var provider = mock(GamePriceProvider.class);
        var repository = mock(GamePriceRepository.class);
        UUID game = UUID.randomUUID();
        when(provider.isConfigured()).thenReturn(true);
        when(repository.claim(any(), any())).thenReturn(true);
        when(repository.candidates(any(), any(), any(), any(), any(), anyInt())).thenReturn(List.of(new GamePriceRepository.Candidate(game, Duration.ofHours(2))));
        when(provider.prices(any(), any())).thenThrow(new ProviderException(GamePriceService.LOCAL_THROTTLE, Duration.ofMinutes(2)));
        new GamePriceService(provider, repository, mock(GameRepository.class), properties("US:USD,GB:GBP"), CLOCK).refresh();
        verify(repository, never()).failed(any(), any(), any(), any(), any(), any());
        verify(repository, never()).publish(any(), any(), any(), any(), any());
        verify(repository, times(1)).release(any());
    }

    @Test void marketsQueuedAfterTheRunBudgetExpiredDoNotRunTheExpensiveCandidateQuery() {
        var provider = mock(GamePriceProvider.class);
        var repository = mock(GamePriceRepository.class);
        var now = new java.util.concurrent.atomic.AtomicReference<>(CLOCK.instant());
        Clock moving = new Clock() {
            @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
        };
        when(provider.isConfigured()).thenReturn(true);
        when(repository.claim(any(), any())).thenReturn(true);
        when(repository.candidates(any(), any(), any(), any(), any(), anyInt())).thenAnswer(call -> {
            now.set(now.get().plus(GamePriceService.RUN_BUDGET).plusSeconds(1));
            return List.of();
        });
        new GamePriceService(provider, repository, mock(GameRepository.class), properties("US:USD,GB:GBP,DE:EUR,TR:TRY"), moving).refresh();
        verify(repository, org.mockito.Mockito.atMost(GamePriceService.PARALLEL_MARKETS)).candidates(any(), any(), any(), any(), any(), anyInt());
        verify(repository, times(1)).release(any());
    }

    @Test void theLeastServedMarketGoesFirstSoNoMarketStaysAtTheTailOfEveryRun() {
        var markets = List.of("US", "GB", "DE", "TR");
        var served = java.util.Map.of("US", 900L, "GB", 100L, "DE", 100L, "TR", 0L);
        assertEquals(List.of("TR", "GB", "DE", "US"), PriceMarketScheduler.leastServedFirst(markets, served::get),
                "en az işlenen önce; eşitlikte yapılandırma sırası");
        assertEquals(markets, PriceMarketScheduler.leastServedFirst(markets, market -> 0L), "hepsi eşitse yapılandırma sırası");
        assertEquals(List.of(), PriceMarketScheduler.leastServedFirst(List.<String>of(), market -> 0L));
    }

    @Test void aMarketWithAFirstFillBacklogGoesFirstSoTheQuotaIsNotSpreadOverMarketsThatAreAlreadyFilled() {
        Market tr = new Market("TR", "TRY");
        var scheduler = new PriceMarketScheduler();
        var configured = List.of(new Market("US", "USD"), new Market("GB", "GBP"), new Market("DE", "EUR"), tr);
        for (int run = 1; run < PriceMarketScheduler.FULL_ROUND_EVERY; run++) scheduler.runMarkets(configured, market -> market.equals(tr));
        assertEquals(List.of(tr, new Market("US", "USD"), new Market("GB", "GBP"), new Market("DE", "EUR")),
                scheduler.runMarkets(configured, market -> market.equals(tr)),
                "tüm pazarların çalıştığı turda birikimli pazar öne, kalanlar yapılandırma sırasıyla");
    }

    @Test void marketsThatRefreshedMoreGamesComeLaterWhenNoMarketHasABacklog() {
        var scheduler = new PriceMarketScheduler();
        Market us = new Market("US", "USD"), gb = new Market("GB", "GBP");
        scheduler.served(us, 500);
        scheduler.served(gb, 0);
        assertEquals(List.of(gb, us), scheduler.runMarkets(List.of(us, gb), market -> false));
        assertEquals(500, scheduler.servedCount(us));
    }

    @Test void runsGoOnlyToTheBacklogMarketExceptEveryFifthRunWhichServesAllMarkets() {
        var repository = mock(GamePriceRepository.class);
        Market tr = new Market("TR", "TRY");
        when(repository.inBacklog(tr)).thenReturn(true);
        var service = new GamePriceService(mock(GamePriceProvider.class), repository, mock(GameRepository.class), properties("US:USD,GB:GBP,TR:TRY"), CLOCK);
        var all = List.of(tr, new Market("US", "USD"), new Market("GB", "GBP"));
        for (int run = 1; run <= 4; run++) assertEquals(List.of(tr), service.runMarkets(), run + ". tur yalnız birikimli pazar");
        assertEquals(all, service.runMarkets(), "5. tur tüm pazarlar");
        assertEquals(List.of(tr), service.runMarkets(), "sayaç döner");
    }

    @Test void withoutABacklogEveryMarketIsServedInEveryRun() {
        var repository = mock(GamePriceRepository.class);
        var service = new GamePriceService(mock(GamePriceProvider.class), repository, mock(GameRepository.class), properties("US:USD,GB:GBP"), CLOCK);
        for (int run = 1; run <= 6; run++) assertEquals(2, service.runMarkets().size());
    }

    @Test void refreshedGamesAreCountedPerMarketAndMarketsWithoutWorkStayAtZero() {
        var provider = mock(GamePriceProvider.class);
        var repository = mock(GamePriceRepository.class);
        Market us = new Market("US", "USD"), gb = new Market("GB", "GBP");
        when(provider.isConfigured()).thenReturn(true);
        when(repository.claim(any(), any())).thenReturn(true);
        when(repository.candidates(eq(us), any(), any(), any(), any(), anyInt())).thenReturn(List.of(new GamePriceRepository.Candidate(UUID.randomUUID(), Duration.ofHours(2))));
        when(repository.candidates(eq(gb), any(), any(), any(), any(), anyInt())).thenReturn(List.of());
        when(repository.publish(any(), any(), any(), any(), any())).thenReturn(true);
        when(provider.prices(any(), any())).thenReturn(List.of());
        var service = new GamePriceService(provider, repository, mock(GameRepository.class), properties("US:USD,GB:GBP"), CLOCK);
        service.refresh();
        service.refresh();
        assertEquals(2, service.servedCount(us), "iki turda birer oyun");
        assertEquals(0, service.servedCount(gb), "işi olmayan pazar sıfırda kalır ve sonraki turda öne geçer");
    }


    @Test void aRunWhoseLeaseIsHeldElsewhereIsSkippedWithoutCallingTheProvider() {
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

    /** Gizleme süresi, kademenin yenileme aralığının {@code displayGrace} (3) katıdır; kaynağın kendi bitişi (satış sonu) daha erkense o geçerlidir. */
    @Test void eachGamesQuoteIsShortenedToItsTierDisplayTimeAndNeverExtended() {
        var provider = mock(GamePriceProvider.class);
        var repository = mock(GamePriceRepository.class);
        UUID hot = UUID.randomUUID(), cold = UUID.randomUUID();
        when(provider.isConfigured()).thenReturn(true);
        when(repository.claim(any(), any())).thenReturn(true);
        when(repository.candidates(any(), any(), any(), any(), any(), anyInt())).thenReturn(List.of(
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
        assertEquals(fetched.plus(Duration.ofHours(6)), published.get(0).expiresAt(), "sıcak oyun: 2 saatlik aralığın 3 katı = 6 saat");
        assertEquals(fetched.plus(Duration.ofHours(48)), published.get(1).expiresAt(), "soğuk oyun: 48 saatlik aralığın 3 katı kaynağın 48 saatlik üst sınırını aşar, uzatılmaz");
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
        for (int index = 0; index < 200 * GamePriceService.MAX_BATCHES_PER_RUN; index++) full.add(new GamePriceRepository.Candidate(UUID.randomUUID(), Duration.ofHours(48)));
        when(repository.candidates(any(), any(), any(), any(), any(), anyInt())).thenReturn(full);
        new GamePriceService(provider, repository, mock(GameRepository.class), properties(""), CLOCK).refresh();
        verify(provider, times(GamePriceService.MAX_BATCHES_PER_RUN)).prices(any(), any());
        verify(repository, times(1)).candidates(any(), any(), any(), any(), any(), eq(200 * GamePriceService.MAX_BATCHES_PER_RUN));
        verify(repository, times(1)).release(any());

        var shortProvider = mock(GamePriceProvider.class);
        var shortRepository = mock(GamePriceRepository.class);
        when(shortProvider.isConfigured()).thenReturn(true);
        when(shortRepository.claim(any(), any())).thenReturn(true);
        when(shortRepository.publish(any(), any(), any(), any(), any())).thenReturn(true);
        when(shortProvider.prices(any(), any())).thenReturn(List.of());
        when(shortRepository.candidates(any(), any(), any(), any(), any(), anyInt())).thenReturn(List.of(new GamePriceRepository.Candidate(UUID.randomUUID(), Duration.ofHours(2))));
        new GamePriceService(shortProvider, shortRepository, mock(GameRepository.class), properties(""), CLOCK).refresh();
        verify(shortProvider, times(1)).prices(any(), any());
    }

    @Test void aLostLeaseStopsTheRemainingBatchesOfTheRun() {
        var provider = mock(GamePriceProvider.class);
        var repository = mock(GamePriceRepository.class);
        when(provider.isConfigured()).thenReturn(true);
        when(repository.claim(any(), any())).thenReturn(true);
        when(repository.publish(any(), any(), any(), any(), any())).thenReturn(false);
        when(provider.prices(any(), any())).thenReturn(List.of());
        var many = new java.util.ArrayList<GamePriceRepository.Candidate>();
        for (int index = 0; index < 600; index++) many.add(new GamePriceRepository.Candidate(UUID.randomUUID(), Duration.ofHours(48)));
        when(repository.candidates(any(), any(), any(), any(), any(), anyInt())).thenReturn(many);
        new GamePriceService(provider, repository, mock(GameRepository.class), properties(""), CLOCK).refresh();
        verify(provider, times(1)).prices(any(), any());
        verify(repository, times(1)).release(any());
    }

    @Test void tiersAreMultiplesOfTheHotFreshnessAndTheTtlHasASafeRange() {
        var properties = new GamePriceProperties("k", API, "US", "USD", Duration.ofHours(2), "");
        assertEquals(Duration.ofHours(12), properties.warmTtl());
        assertEquals(Duration.ofHours(48), properties.coldTtl());
        assertThrows(IllegalArgumentException.class, () -> new GamePriceProperties("k", API, "US", "USD", Duration.ofHours(3), ""));
        assertThrows(IllegalArgumentException.class, () -> new GamePriceProperties("k", API, "US", "USD", Duration.ZERO, ""));
    }
}

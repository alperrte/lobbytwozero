package com.ltz.game.application.service;

import com.ltz.game.application.port.GamePriceProvider;
import com.ltz.game.application.port.GamePriceProvider.Market;
import com.ltz.game.application.port.GamePriceProvider.Quote;
import com.ltz.game.application.port.GameProvider;
import com.ltz.game.domain.entity.Game;
import com.ltz.game.domain.entity.GameExternalReference;
import com.ltz.game.domain.exception.ProviderException;
import com.ltz.game.infrastructure.config.GamePriceProperties;
import com.ltz.game.infrastructure.config.GameRefreshProperties;
import com.ltz.game.infrastructure.persistence.repository.GameExternalReferenceRepository;
import com.ltz.game.infrastructure.persistence.repository.GamePriceRepository;
import com.ltz.game.infrastructure.persistence.repository.GameRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Fiyat birleştirme: Steam'in kendi fiyatı sıcak oyunlarda, ITAD boşluğunda ve sayfası açılan oyunlarda doğrudan Steam'den alınır ve ITAD'in
 * "Steam" satırını ezer; Steam başarısız olursa ITAD satırı kalır; Steam'in ücretsiz/yakında dediği oyun için boşluk yedeği sorulmaz.
 */
class GamePriceSteamMergeTest {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-08T10:00:00Z"), ZoneOffset.UTC);
    static final String API = "https://api.isthereanydeal.com";
    static final Market US = new Market("US", "USD");
    static final Duration HOT = Duration.ofMinutes(30);

    final GamePriceProvider itad = mock(GamePriceProvider.class);
    final GamePriceRepository repository = mock(GamePriceRepository.class);
    final GameProvider steam = mock(GameProvider.class);
    final GameExternalReferenceRepository references = mock(GameExternalReferenceRepository.class);
    final UUID hot = UUID.randomUUID(), cold = UUID.randomUUID(), gap = UUID.randomUUID(), soon = UUID.randomUUID();
    final Map<UUID, String> apps = Map.of(hot, "100", cold, "200", gap, "300", soon, "400");

    private static GameRefreshProperties refresh(boolean hotDirect, boolean gapFallback, String steamMarkets) {
        return new GameRefreshProperties(null, null, 90, 30, new GameRefreshProperties.SteamPrice(hotDirect, gapFallback, steamMarkets), null);
    }

    private GamePriceService service(boolean itadConfigured, boolean hotDirect, boolean gapFallback) {
        return service(itadConfigured, hotDirect, gapFallback, "GB:GBP", "US:USD,GB:GBP");
    }

    private GamePriceService service(boolean itadConfigured, boolean hotDirect, boolean gapFallback, String priceMarkets, String steamMarkets) {
        var properties = new GamePriceProperties("fixture-key", API, "US", "USD", HOT, priceMarkets);
        when(itad.isConfigured()).thenReturn(itadConfigured);
        when(steam.storePricesEnabled()).thenReturn(true);
        when(repository.claim(any(), any())).thenReturn(true);
        when(repository.publish(any(), any(), any(), any(), any())).thenReturn(true);
        var refs = apps.entrySet().stream().map(entry -> {
            var game = new Game();
            game.setId(entry.getKey());
            var ref = new GameExternalReference();
            ref.setGame(game);
            ref.setProvider("STEAM");
            ref.setExternalId(entry.getValue());
            return ref;
        }).toList();
        when(references.findByGameIdIn(any())).thenAnswer(call -> refs.stream().filter(ref -> ((java.util.Collection<?>) call.getArgument(0)).contains(ref.getGame().getId())).toList());
        return new GamePriceService(itad, repository, mock(GameRepository.class), properties, CLOCK, steam, references, refresh(hotDirect, gapFallback, steamMarkets), null);
    }

    private static Quote itadQuote(UUID game, String store, String current, String regular) {
        Instant fetched = CLOCK.instant();
        return new Quote(game, store, US, new BigDecimal(regular), new BigDecimal(current), "ITAD", fetched, fetched.plus(Duration.ofHours(48)),
                "https://itad.link/" + store, 0, fetched.minusSeconds(5));
    }

    private void candidates(GamePriceRepository.Candidate... values) {
        when(repository.candidates(any(), any(), any(), any(), any(), anyInt())).thenReturn(List.of(values));
    }

    @SuppressWarnings("unchecked")
    private List<Quote> published(Market market) {
        var captured = ArgumentCaptor.forClass(List.class);
        verify(repository).publish(any(), any(), eq(market), captured.capture(), any());
        return captured.getValue();
    }

    @Test void inAMarketWhereSteamsCurrencyIsNotVerifiedItadsSteamLineIsNeverShownButEveryOtherStoreStays() {
        Market tr = new Market("TR", "TRY");
        var service = service(true, false, false, "TR:TRY", "US:USD,GB:GBP");
        candidates(new GamePriceRepository.Candidate(hot, HOT));
        Instant fetched = CLOCK.instant();
        java.util.function.BiFunction<String, String, Quote> trQuote = (store, price) -> new Quote(hot, store, tr, new BigDecimal(price), new BigDecimal(price), "ITAD",
                fetched, fetched.plus(Duration.ofHours(48)), "https://itad.link/" + store, 0, fetched.minusSeconds(5));
        when(itad.prices(any(), eq(tr))).thenReturn(List.of(trQuote.apply("Steam", "300"), trQuote.apply("GOG", "250"), trQuote.apply("Epic Game Store", "230"),
                trQuote.apply("Fanatical", "280"), trQuote.apply("Humble Store", "285"), trQuote.apply("GreenManGaming", "260"), trQuote.apply("IndieGala", "270")));
        service.refresh();
        var quotes = published(tr);
        assertEquals(6, quotes.size(), "ITAD'in Steam TRY satırı atılır, diğer altı mağaza tutulur");
        assertTrue(quotes.stream().noneMatch(quote -> quote.store().equals("Steam")));
        verify(steam, never()).storePrices(any(), any());
    }

    @Test void aHotGamesSteamLineComesDirectlyFromSteamAndReplacesItadsSteamLineWhileOtherStoresStay() {
        var service = service(true, true, false);
        candidates(new GamePriceRepository.Candidate(hot, HOT), new GamePriceRepository.Candidate(cold, Duration.ofHours(12)));
        when(itad.prices(any(), eq(US))).thenReturn(List.of(itadQuote(hot, "Steam", "9.99", "9.99"), itadQuote(hot, "GOG", "8.00", "10.00"),
                itadQuote(cold, "Steam", "5.00", "5.00")));
        when(steam.storePrices(any(), eq("US"))).thenReturn(Map.of("100", new GameProvider.StorePrice(699, 1999, 65, CLOCK.instant().plus(Duration.ofHours(1)))));
        service.refresh();
        verify(steam).storePrices(List.of("100"), "US");
        var quotes = published(US);
        assertEquals(3, quotes.size());
        var steamLine = quotes.stream().filter(q -> q.gameId().equals(hot) && q.store().equals("Steam")).findFirst().orElseThrow();
        assertEquals("STEAM", steamLine.source());
        assertEquals(new BigDecimal("6.99"), steamLine.currentPrice());
        assertEquals(new BigDecimal("19.99"), steamLine.regularPrice());
        assertEquals(65, steamLine.discountPercent());
        assertEquals("https://store.steampowered.com/app/100", steamLine.url());
        assertEquals(CLOCK.instant().plus(Duration.ofHours(1)), steamLine.expiresAt(), "indirim bitişi gizleme süresinden erkenken o geçerli");
        assertTrue(quotes.stream().anyMatch(q -> q.gameId().equals(hot) && q.store().equals("GOG") && q.source().equals("ITAD")));
        var coldSteam = quotes.stream().filter(q -> q.gameId().equals(cold)).findFirst().orElseThrow();
        assertEquals("ITAD", coldSteam.source(), "sıcak olmayan oyunun Steam satırı ITAD'den kalır");
        assertEquals(CLOCK.instant().plus(Duration.ofHours(36)), coldSteam.expiresAt(), "ITAD teklifi 12 saatlik aralığın 3 katı kadar gösterilir");
    }

    @Test void aSteamFailureKeepsItadsSteamLineAndDoesNotFailTheRefreshWhenItadIsConfigured() {
        var service = service(true, true, false);
        candidates(new GamePriceRepository.Candidate(hot, HOT));
        when(itad.prices(any(), eq(US))).thenReturn(List.of(itadQuote(hot, "Steam", "9.99", "9.99")));
        when(steam.storePrices(any(), eq("US"))).thenThrow(new ProviderException("RATE_LIMITED"));
        service.refresh();
        var quotes = published(US);
        assertEquals(1, quotes.size());
        assertEquals("ITAD", quotes.getFirst().source());
        verify(repository, never()).failed(any(), any(), any(), any(), any(), any());
    }

    @Test void whenSteamIsAskedAndHasNoPriceItadsSteamLineIsDroppedBecauseSteamIsAuthoritative() {
        var service = service(true, true, false);
        candidates(new GamePriceRepository.Candidate(hot, HOT));
        when(itad.prices(any(), eq(US))).thenReturn(List.of(itadQuote(hot, "Steam", "9.99", "9.99"), itadQuote(hot, "GOG", "8.00", "8.00")));
        when(steam.storePrices(any(), eq("US"))).thenReturn(Map.of());
        service.refresh();
        var quotes = published(US);
        assertEquals(List.of("GOG"), quotes.stream().map(Quote::store).toList());
    }

    @Test void theGapFallbackAsksSteamOnlyForGamesItadHasNoSteamLineForAndSkipsFreeOrComingSoonGames() {
        var service = service(true, false, true);
        candidates(new GamePriceRepository.Candidate(cold, Duration.ofHours(12)), new GamePriceRepository.Candidate(gap, Duration.ofHours(12)),
                new GamePriceRepository.Candidate(soon, Duration.ofHours(12)));
        when(itad.prices(any(), eq(US))).thenReturn(List.of(itadQuote(cold, "Steam", "5.00", "5.00")));
        when(repository.withoutSteamPrice(any())).thenReturn(Set.of(soon));
        when(steam.storePrices(any(), eq("US"))).thenReturn(Map.of("300", new GameProvider.StorePrice(299, 299, 0, null)));
        service.refresh();
        verify(steam).storePrices(List.of("300"), "US");
        var quotes = published(US);
        var gapQuote = quotes.stream().filter(q -> q.gameId().equals(gap)).findFirst().orElseThrow();
        assertEquals("STEAM", gapQuote.source());
        assertEquals(new BigDecimal("2.99"), gapQuote.currentPrice());
        assertEquals(CLOCK.instant().plus(Duration.ofHours(36)), gapQuote.expiresAt());
        assertEquals(Set.of(cold, gap), Set.copyOf(quotes.stream().map(Quote::gameId).toList()));
    }

    @Test void steamIsNotAskedForAMarketWhoseCurrencyHasNotBeenVerifiedButItadStillRefreshesIt() {
        // ITAD pazarları ABD ve Almanya; Steam para birimi yalnız ABD için doğrulanmış.
        var service = service(true, true, true, "DE:EUR", "US:USD");
        candidates(new GamePriceRepository.Candidate(hot, HOT));
        when(itad.prices(any(), any())).thenReturn(List.of());
        service.refresh();
        verify(steam).storePrices(any(), eq("US"));
        verify(steam, never()).storePrices(any(), eq("DE"));
        verify(itad).prices(any(), eq(new Market("DE", "EUR")));
        verify(repository).publish(any(), any(), eq(new Market("DE", "EUR")), any(), any());
    }

    @Test void withoutAnItadKeyOnlyHotGamesAreRefreshedFromSteamAndASteamFailureMarksThemFailed() {
        var service = service(false, true, true);
        candidates(new GamePriceRepository.Candidate(hot, HOT), new GamePriceRepository.Candidate(cold, Duration.ofHours(12)));
        when(steam.storePrices(any(), eq("US"))).thenThrow(new ProviderException("RATE_LIMITED"));
        service.refresh();
        verify(itad, never()).prices(any(), any());
        verify(steam).storePrices(List.of("100"), "US");
        verify(repository).failed(any(), eq(Set.of(hot)), eq(US), any(), eq("RATE_LIMITED"), any());
    }

    @Test void nothingRunsWithoutAnItadKeyWhenTheDirectSteamHotRefreshIsOff() {
        var service = service(false, false, true);
        service.refresh();
        verify(repository, never()).claim(any(), any());
    }

    @Test void gamesWhosePageIsOpenedAreRefreshedNowWithTheirOwnTierAndAlwaysFromSteam() {
        var service = service(true, false, false);
        when(repository.tiers(any(), any(), any(), any())).thenReturn(Map.of(cold, Duration.ofHours(12)));
        when(itad.prices(any(), eq(US))).thenReturn(List.of(itadQuote(cold, "Steam", "5.00", "5.00")));
        when(steam.storePrices(any(), eq("US"))).thenReturn(Map.of("200", new GameProvider.StorePrice(450, 450, 0, null)));
        assertEquals(1, service.refreshNow(List.of(cold, UUID.randomUUID()), US));
        var quotes = published(US);
        assertEquals("STEAM", quotes.getFirst().source());
        assertEquals(new BigDecimal("4.50"), quotes.getFirst().currentPrice(), "hotDirect kapalı olsa da sayfası açılan oyun Steam'den alınır");
        assertEquals(CLOCK.instant().plus(Duration.ofHours(36)), quotes.getFirst().expiresAt(), "oyunun kendi kademesi (12 sa) × 3");
        verify(repository).release(any());
    }

    @Test void refreshNowDoesNothingWhenTheLeaseIsHeldElsewhereTheMarketIsNotConfiguredOrNoGameIsEligible() {
        var service = service(true, false, false);
        when(repository.tiers(any(), any(), any(), any())).thenReturn(Map.of(cold, Duration.ofHours(12)));
        when(repository.claim(any(), any())).thenReturn(false);
        assertEquals(-1, service.refreshNow(List.of(cold), US), "kiralama başka yerde");
        assertEquals(-1, service.refreshNow(List.of(cold), new Market("DE", "EUR")), "yapılandırılmamış pazar");
        assertEquals(-1, service.refreshNow(List.of(), US));
        when(repository.claim(any(), any())).thenReturn(true);
        when(repository.tiers(any(), any(), any(), any())).thenReturn(Map.of());
        assertEquals(-1, service.refreshNow(List.of(UUID.randomUUID()), US), "yenilenebilir oyun yok");
        verify(itad, never()).prices(any(), any());
    }
}

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Fiyat yenilemede hata yönetimi: kota ve yerel kısıtlama oyunu FAILED yapmaz, hata türüne göre bekleme yazılır (aynı parti her turda başa gelmez),
 * veri hatası veren parti bölünerek zehirli oyun ayıklanır, ITAD bloktayken ağır aday sorgusu çalışmaz.
 */
class GamePriceFailureHandlingTest {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T10:00:00Z"), ZoneOffset.UTC);
    static final Market US = new Market("US", "USD");

    private static GamePriceProperties properties() {
        return new GamePriceProperties("fixture-key", "https://api.isthereanydeal.com", "US", "USD", Duration.ofMinutes(15), "US:USD");
    }

    private record Fixture(GamePriceProvider provider, GamePriceRepository repository, GamePriceService service) { }

    private static Fixture fixture(List<UUID> games) {
        var provider = mock(GamePriceProvider.class);
        var repository = mock(GamePriceRepository.class);
        when(provider.isConfigured()).thenReturn(true);
        when(repository.claim(any(), any())).thenReturn(true);
        when(repository.publish(any(), any(), any(), any(), any())).thenReturn(true);
        when(repository.candidates(any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(games.stream().map(id -> new GamePriceRepository.Candidate(id, Duration.ofHours(2))).toList());
        return new Fixture(provider, repository, new GamePriceService(provider, repository, mock(GameRepository.class), properties(), CLOCK));
    }

    private static List<UUID> games(int count) { return IntStream.range(0, count).mapToObj(index -> UUID.randomUUID()).toList(); }

    @Test void anItadBlockSkipsTheExpensiveCandidateQueryAndWritesNothing() {
        var f = fixture(games(3));
        when(f.provider().isBlocked()).thenReturn(true);
        f.service().refresh();
        verify(f.repository(), never()).candidates(any(), any(), any(), any(), any(), anyInt());
        verify(f.repository(), never()).failed(any(), any(), any(), any(), any(), any());
        verify(f.provider(), never()).prices(any(), any());
        verify(f.repository(), times(1)).release(any());
    }

    @Test void anItad429IsTransientAndNeverMarksTheGamesAsFailed() {
        var f = fixture(games(3));
        when(f.provider().prices(any(), any())).thenThrow(new ProviderException("PRICE_RATE_LIMITED", Duration.ofSeconds(86)));
        f.service().refresh();
        verify(f.repository(), never()).failed(any(), any(), any(), any(), any(), any());
        verify(f.repository(), never()).publish(any(), any(), any(), any(), any());
    }

    @Test void theProviderBlockCodeIsTransientToo() {
        var f = fixture(games(2));
        when(f.provider().prices(any(), any())).thenThrow(new ProviderException(GamePriceService.PROVIDER_BLOCKED, Duration.ofSeconds(30)));
        f.service().refresh();
        verify(f.repository(), never()).failed(any(), any(), any(), any(), any(), any());
    }

    @Test void errorsWithoutRetryAfterGetAnExplicitBackoffSoTheSameBatchIsNotRetriedEveryRound() {
        var games = games(2);
        var f = fixture(games);
        when(f.provider().prices(any(), any())).thenThrow(new ProviderException("PRICE_UNAVAILABLE"));
        f.service().refresh();
        verify(f.repository()).failed(any(), eq(Set.copyOf(games)), eq(US), any(), eq("PRICE_UNAVAILABLE"), eq(PriceFailurePolicy.UNAVAILABLE_BACKOFF));

        var denied = fixture(games);
        when(denied.provider().prices(any(), any())).thenThrow(new ProviderException("PRICE_ACCESS_DENIED"));
        denied.service().refresh();
        verify(denied.repository()).failed(any(), any(), eq(US), any(), eq("PRICE_ACCESS_DENIED"), eq(PriceFailurePolicy.ACCESS_BACKOFF));
    }

    @Test void theBackoffPrefersTheProviderRetryAfterThenTheErrorKind() {
        assertEquals(Duration.ofSeconds(7), PriceFailurePolicy.backoffFor(new ProviderException("RATE_LIMITED", Duration.ofSeconds(7))));
        assertEquals(PriceFailurePolicy.DATA_ERROR_BACKOFF, PriceFailurePolicy.backoffFor(new ProviderException("AMBIGUOUS_PRICE_MAPPING")));
        assertEquals(PriceFailurePolicy.ACCESS_BACKOFF, PriceFailurePolicy.backoffFor(new ProviderException("PRICE_NOT_CONFIGURED")));
        assertEquals(PriceFailurePolicy.UNAVAILABLE_BACKOFF, PriceFailurePolicy.backoffFor(new ProviderException("SOMETHING_ELSE")));
    }

    @Test void aPoisonGameIsIsolatedByBisectionAndOnlyItIsBackedOffWhileTheOthersArePublished() {
        var games = games(4);
        UUID poison = games.get(3);
        var f = fixture(games);
        when(f.provider().prices(any(), any())).thenAnswer(invocation -> {
            Set<UUID> ids = invocation.getArgument(0);
            if (ids.contains(poison)) throw new ProviderException("AMBIGUOUS_PRICE_MAPPING");
            return List.of();
        });
        f.service().refresh();
        verify(f.repository(), times(1)).failed(any(), any(), any(), any(), any(), any());
        verify(f.repository()).failed(any(), eq(Set.of(poison)), eq(US), any(), eq("AMBIGUOUS_PRICE_MAPPING"), eq(PriceFailurePolicy.DATA_ERROR_BACKOFF));
        verify(f.repository()).publish(any(), eq(Set.of(games.get(0), games.get(1))), eq(US), any(), any());
        verify(f.repository()).publish(any(), eq(Set.of(games.get(2))), eq(US), any(), any());
        verify(f.repository(), times(2)).publish(any(), any(), any(), any(), any());
    }

    @Test void whenNothingSucceedsTheErrorIsNotGameSpecificSoNoGameGetsTheLongBackoff() {
        var games = games(4);
        var f = fixture(games);
        when(f.provider().prices(any(), any())).thenThrow(new IllegalArgumentException("Geçersiz bölgesel fiyat verisi."));
        f.service().refresh();
        verify(f.repository(), times(1)).failed(any(), any(), any(), any(), any(), any());
        verify(f.repository()).failed(any(), eq(Set.copyOf(games)), eq(US), any(), eq("INVALID_PRICE_RESPONSE"), eq(PriceFailurePolicy.WHOLE_BATCH_BACKOFF));
        verify(f.repository(), never()).publish(any(), any(), any(), any(), any());
    }

    @Test void isolationSpendsAtMostTheBudgetOfExtraBatchesEvenWhenEveryGamePoisonsItsBatch() {
        var f = fixture(games(200));
        when(f.provider().prices(any(), any())).thenThrow(new ProviderException("INVALID_PRICE_RESPONSE"));
        f.service().refresh();
        verify(f.provider(), times(1 + PriceFailurePolicy.ISOLATION_BATCH_BUDGET)).prices(any(), any());
        verify(f.repository(), times(1)).failed(any(), any(), any(), any(), any(), eq(PriceFailurePolicy.WHOLE_BATCH_BACKOFF));
    }

    @Test void aTransientErrorDuringIsolationAbortsWithoutMarkingAnyGameAsPoisoned() {
        var games = games(4);
        var f = fixture(games);
        var calls = new ArrayList<Integer>();
        when(f.provider().prices(any(), any())).thenAnswer(invocation -> {
            calls.add(calls.size());
            if (calls.size() == 1) throw new ProviderException("INVALID_PRICE_RESPONSE");
            throw new ProviderException("PRICE_UNAVAILABLE");
        });
        f.service().refresh();
        assertEquals(2, calls.size(), "ilk hata bölmeyi başlatır, ikinci (geçici) hata durdurur");
        verify(f.repository(), times(1)).failed(any(), any(), any(), any(), eq("PRICE_UNAVAILABLE"), eq(PriceFailurePolicy.UNAVAILABLE_BACKOFF));
    }

    @Test void aSinglePoisonedBatchOfOneGameIsBackedOffWithoutBisection() {
        var games = games(1);
        var f = fixture(games);
        when(f.provider().prices(any(), any())).thenThrow(new ProviderException("PRICE_CURRENCY_UNAVAILABLE"));
        f.service().refresh();
        verify(f.provider(), times(1)).prices(any(), any());
        verify(f.repository()).failed(any(), eq(Set.copyOf(games)), eq(US), any(), eq("PRICE_CURRENCY_UNAVAILABLE"), eq(PriceFailurePolicy.DATA_ERROR_BACKOFF));
    }

    @Test void aViewedGamesTemporaryFailuresAreReportedAsRetryableWithTheirDelayAndPermanentOnesAreNot() {
        UUID one = UUID.randomUUID();
        var blocked = fixture(games(1));
        when(blocked.provider().isBlocked()).thenReturn(true);
        when(blocked.provider().blockedFor()).thenReturn(Duration.ofSeconds(45));
        assertEquals(RefreshNowResult.retry(Duration.ofSeconds(45)), blocked.service().refreshNowResult(Set.of(one), US));

        var limited = fixture(games(1));
        when(limited.repository().tiers(any(), any(), any(), any())).thenReturn(Map.of(one, Duration.ofHours(2)));
        when(limited.provider().prices(any(), any(), any())).thenThrow(new ProviderException("PRICE_RATE_LIMITED", Duration.ofSeconds(86)));
        assertEquals(RefreshNowResult.retry(Duration.ofSeconds(86)), limited.service().refreshNowResult(Set.of(one), US));
        verify(limited.repository(), never()).failed(any(), any(), any(), any(), any(), any());

        var leased = fixture(games(1));
        when(leased.repository().tiers(any(), any(), any(), any())).thenReturn(Map.of(one, Duration.ofHours(2)));
        when(leased.repository().claim(any(), any())).thenReturn(false);
        leased.service().sleeperForTests(millis -> { });
        assertEquals(RefreshNowResult.retry(GamePriceService.CLAIM_RETRY), leased.service().refreshNowResult(Set.of(one), US));

        var data = fixture(games(1));
        when(data.repository().tiers(any(), any(), any(), any())).thenReturn(Map.of(one, Duration.ofHours(2)));
        when(data.provider().prices(any(), any(), any())).thenThrow(new ProviderException("AMBIGUOUS_PRICE_MAPPING"));
        assertEquals(RefreshNowResult.NOT_REFRESHED, data.service().refreshNowResult(Set.of(one), US));

        var unconfigured = fixture(games(1));
        assertEquals(RefreshNowResult.NOT_REFRESHED, unconfigured.service().refreshNowResult(Set.of(), US));
        assertEquals(RefreshNowResult.NOT_REFRESHED, unconfigured.service().refreshNowResult(Set.of(one), new Market("DE", "EUR")));
    }

    @Test void aViewedGameReturnsImmediatelyWhileItadIsBlockedWithoutTakingTheLease() {
        var f = fixture(games(1));
        when(f.provider().isBlocked()).thenReturn(true);
        assertEquals(-1, f.service().refreshNow(Set.of(UUID.randomUUID()), US));
        verify(f.repository(), never()).tiers(any(), any(), any(), any());
        verify(f.repository(), never()).claim(any(), any());
    }

    @Test void aViewedGamesDataErrorUsesTheFullBackoffForOneGameAndTheShortOneForSeveral() {
        UUID one = UUID.randomUUID();
        var single = fixture(games(1));
        when(single.repository().tiers(any(), any(), any(), any())).thenReturn(Map.of(one, Duration.ofHours(2)));
        when(single.provider().prices(any(), any(), any())).thenThrow(new IllegalArgumentException("Geçersiz bölgesel fiyat verisi."));
        assertEquals(-1, single.service().refreshNow(Set.of(one), US));
        verify(single.repository()).failed(any(), eq(Set.of(one)), eq(US), any(), eq("INVALID_PRICE_RESPONSE"), eq(PriceFailurePolicy.DATA_ERROR_BACKOFF));

        UUID other = UUID.randomUUID();
        var several = fixture(games(1));
        when(several.repository().tiers(any(), any(), any(), any())).thenReturn(Map.of(one, Duration.ofHours(2), other, Duration.ofHours(2)));
        when(several.provider().prices(any(), any(), any())).thenThrow(new ProviderException("AMBIGUOUS_PRICE_MAPPING"));
        assertEquals(-1, several.service().refreshNow(Set.of(one, other), US));
        verify(several.repository()).failed(any(), eq(Set.of(one, other)), eq(US), any(), eq("AMBIGUOUS_PRICE_MAPPING"), eq(PriceFailurePolicy.WHOLE_BATCH_BACKOFF));
    }
}

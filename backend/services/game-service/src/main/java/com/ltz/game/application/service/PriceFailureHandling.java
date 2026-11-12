package com.ltz.game.application.service;

import com.ltz.game.application.port.GamePriceProvider.Market;
import com.ltz.game.domain.exception.ProviderException;
import com.ltz.game.infrastructure.persistence.repository.GamePriceRepository;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Fiyat yenilemesinde hata kaydı ve zehirli oyun ayıklama. {@link PriceFailurePolicy} neyin kaydedileceğine ve ne kadar bekletileceğine karar verir;
 * bu sınıf kaydı yazar. Kiralama sahibi ({@code owner}) olmayan yazıcı hiçbir kayıt yazamaz ({@link GamePriceRepository#failed}).
 */
final class PriceFailureHandling {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(PriceFailureHandling.class);

    /** Bir toplu yenilemenin sonucu: tamamlanan oyun sayısı, kiralama kaybı ve (hiçbir parça başarılı olmadıysa) partinin tamamının hata kodu. */
    record BatchResult(int refreshed, boolean leaseLost, String wholeBatchCode) { }

    private final GamePriceRepository repository;
    private final Clock clock;

    PriceFailureHandling(GamePriceRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Geçici hata (kota, yerel kısıtlama) kayıt yazmaz; diğerleri hata türüne göre bekletilir. */
    void markFailed(UUID owner, Set<UUID> ids, Market market, ProviderException error) {
        if (PriceFailurePolicy.isTransient(error.code())) return;
        repository.failed(owner, ids, market, clock.instant(), error.code(), PriceFailurePolicy.backoffFor(error));
    }

    /**
     * Sayfası açılan birkaç oyun tek partide veri hatası verdiyse hangisinin zehirli olduğu bilinmez: kısa bekleme; tek oyunda tam bekleme.
     * Geçici neden (kota, yerel kısıtlama, ITAD bloğu) ipucunun sonra yeniden denenebileceğini bildirir.
     */
    RefreshNowResult recordNowFailure(UUID owner, Set<UUID> ids, Market market, RuntimeException raw) {
        ProviderException error = PriceFailurePolicy.asProvider(raw);
        if (ids.size() > 1 && PriceFailurePolicy.isDataError(error.code())) error = new ProviderException(error.code(), PriceFailurePolicy.WHOLE_BATCH_BACKOFF);
        markFailed(owner, ids, market, error);
        return PriceFailurePolicy.isTransient(error.code()) ? RefreshNowResult.retry(error.retryAfter()) : RefreshNowResult.NOT_REFRESHED;
    }

    /**
     * Partiyi yeniler. Veri hatası bir oyuna özgü olabilir: parti bölünerek zehirli oyun ayıklanır. Geçici hatada (kota, ağ) bölme kota harcar, bu yüzden
     * yapılmaz ve hata çağırana fırlatılır.
     */
    BatchResult refreshOrIsolate(UUID owner, Market market, List<GamePriceRepository.Candidate> batch, Set<UUID> ids, boolean itad, PriceBatchIsolator.Refresher refresher) {
        try {
            boolean published = refresher.refresh(batch, ids);
            return new BatchResult(published ? batch.size() : 0, !published, null);
        } catch (ProviderException | IllegalArgumentException raw) {
            if (!itad || batch.size() < 2 || !PriceFailurePolicy.isDataError(PriceFailurePolicy.asProvider(raw).code())) throw raw;
            return isolate(owner, market, batch, refresher);
        }
    }

    private BatchResult isolate(UUID owner, Market market, List<GamePriceRepository.Candidate> batch, PriceBatchIsolator.Refresher refresher) {
        var isolator = new PriceBatchIsolator(refresher);
        isolator.run(batch);
        if (isolator.refreshed() == 0 && !isolator.leaseLost()) return new BatchResult(0, false, isolator.code());
        for (UUID id : isolator.poisoned()) {
            repository.failed(owner, Set.of(id), market, clock.instant(), isolator.code(), PriceFailurePolicy.DATA_ERROR_BACKOFF);
            LOG.warn("Zehirli fiyat oyunu ayıklandı: pazar={}-{} zehirli_oyun={} kod={}", market.country(), market.currency(), id, isolator.code());
        }
        return new BatchResult(isolator.refreshed(), isolator.leaseLost(), null);
    }
}

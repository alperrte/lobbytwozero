package com.ltz.game.application.service;

import com.ltz.game.domain.exception.ProviderException;
import com.ltz.game.infrastructure.persistence.repository.GamePriceRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Veri hatası veren partiyi ikiye bölerek zehirli oyunu ayıklar (en çok {@value PriceFailurePolicy#ISOLATION_BATCH_BUDGET} ek parti). Tek oyuna inen ve
 * hata veren oyun "zehirli" sayılır. Hiçbir parça başarılı olmadıysa hata oyuna özgü değildir: çağıran tüm partiyi kısa süre bekletir, hiçbir oyun uzun
 * süre bekletilmez. Geçici hata (kota, ağ) bölmeyi durdurur ve çağıana fırlatılır; bölme kota harcadığından yalnız veri hatasında yapılır.
 */
final class PriceBatchIsolator {
    /** Bir parçayı yeniler; sonuç kiralama hâlâ bizdeyken yayımlandıysa true. */
    @FunctionalInterface
    interface Refresher { boolean refresh(List<GamePriceRepository.Candidate> part, Set<UUID> ids); }

    private final Refresher refresher;
    private final List<UUID> poisoned = new ArrayList<>();
    private int budget = PriceFailurePolicy.ISOLATION_BATCH_BUDGET;
    private int refreshed;
    private boolean leaseLost;
    private String code = PriceFailurePolicy.INVALID_RESPONSE;

    PriceBatchIsolator(Refresher refresher) { this.refresher = refresher; }

    void run(List<GamePriceRepository.Candidate> batch) {
        int middle = batch.size() / 2;
        attempt(batch.subList(0, middle));
        attempt(batch.subList(middle, batch.size()));
    }

    private void attempt(List<GamePriceRepository.Candidate> part) {
        if (leaseLost || budget <= 0) return;
        budget--;
        Set<UUID> ids = part.stream().map(GamePriceRepository.Candidate::id).collect(Collectors.toUnmodifiableSet());
        try {
            if (refresher.refresh(part, ids)) refreshed += part.size(); else leaseLost = true;
        } catch (ProviderException | IllegalArgumentException raw) {
            ProviderException error = PriceFailurePolicy.asProvider(raw);
            if (!PriceFailurePolicy.isDataError(error.code())) throw raw;
            code = error.code();
            if (part.size() == 1) poisoned.add(part.getFirst().id()); else run(part);
        }
    }

    int refreshed() { return refreshed; }

    boolean leaseLost() { return leaseLost; }

    String code() { return code; }

    List<UUID> poisoned() { return List.copyOf(poisoned); }
}

package com.ltz.lfp.application.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Oyun kataloğunun sahibi game-service'tir (LFP-02, LFP-18); LFP oyunu saklamaz, ilan açılırken doğrular ve yalnız
 * kimliği ile görüntüleme adını tutar. Başka servisin veritabanına erişilmez.
 */
public interface GameCatalogPort {

    /**
     * @return oyun katalogda yayında (ACTIVE) ise özeti; yoksa ya da yayında değilse boş
     * @throws GameCatalogUnavailableException katalog yanıt vermezse ya da yanıt güvenilmezse (çağıran fail-closed
     *                                         davranmalıdır, oyun var ya da yok sayılmaz)
     */
    Optional<CatalogGame> find(UUID gameId);
}

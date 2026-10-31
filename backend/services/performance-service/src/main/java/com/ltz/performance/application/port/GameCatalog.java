package com.ltz.performance.application.port;

import java.util.Optional;
import java.util.UUID;

/** Game Service herkese açık kart sözleşmesi (GAME-15). Game DB'sine erişilmez. */
public interface GameCatalog {
    /** Oyun yoksa veya yayında değilse boş döner; doğrulama yapılamıyorsa fırlatır (kapalı başarısızlık). */
    Optional<CatalogGame> find(UUID gameId);

    record CatalogGame(UUID gameId, String title) { }
}

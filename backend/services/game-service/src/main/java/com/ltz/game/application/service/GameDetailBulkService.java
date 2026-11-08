package com.ltz.game.application.service;

import com.ltz.game.application.port.GameProvider;
import com.ltz.game.domain.entity.Game;
import com.ltz.game.domain.enums.GameOrigin;
import com.ltz.game.domain.enums.GameStatus;
import com.ltz.game.domain.enums.GameType;
import com.ltz.game.domain.exception.ProviderException;
import com.ltz.game.infrastructure.persistence.repository.GameExternalReferenceRepository;
import com.ltz.game.infrastructure.persistence.repository.GameProviderSyncStateRepository;
import com.ltz.game.infrastructure.persistence.repository.GameRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Detayı hiç denenmemiş ana oyunların detayını (açıklama, çıkış tarihi, geliştirici/yayıncı, dil desteği, kapak) tek tek değil
 * toplu (100 AppID / 2 IGDB isteği) alır. Tek kayıt yoluyla aynı kurallar geçerlidir ({@link GameDetailRefreshService#applyFetched}):
 * manuel kilitler ve yönetici verisi ezilmez, provenance yazılır. Sağlayıcı bir oyun için kanıt vermezse o oyun "bulunamadı"
 * olarak işaretlenir ve tekrar sorulmaz (tek kayıt yolundaki politika). Sağlayıcı HATASI işaret yazmaz: tarama duraklar, kayıtlar aday kalır.
 */
@Service
public class GameDetailBulkService {
    static final int BATCH = 100;
    static final Duration IDLE_INTERVAL = Duration.ofMinutes(10);
    private static final Duration MAX_PAUSE = Duration.ofHours(1);
    private static final String PROVIDER_CODE = "STEAM";
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(GameDetailBulkService.class);

    private final GameProvider provider;
    private final GameRepository games;
    private final GameExternalReferenceRepository references;
    private final GameProviderSyncStateRepository states;
    private final GameDetailRefreshService refresh;
    private final ProviderRetryPolicy retries;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private Instant pausedUntil;
    private int consecutiveFailures;
    private long updated;
    private long notFound;

    public GameDetailBulkService(GameProvider provider, GameRepository games, GameExternalReferenceRepository references,
            GameProviderSyncStateRepository states, GameDetailRefreshService refresh, ProviderRetryPolicy retries,
            PlatformTransactionManager transactions, Clock clock) {
        this.provider = provider;
        this.games = games;
        this.references = references;
        this.states = states;
        this.refresh = refresh;
        this.retries = retries;
        this.transaction = new TransactionTemplate(transactions);
        this.clock = clock;
    }

    /** Yalnız testler için. */
    synchronized void resetForTests() {
        pausedUntil = null;
        consecutiveFailures = 0;
        updated = 0;
        notFound = 0;
    }

    /**
     * Bir toplu adım.
     *
     * @return bu adımda işlenen oyun sayısı; iş yoksa, duraklatılmışsa veya yetenek yoksa {@code -1}
     */
    public synchronized int bootstrapNextBatch() {
        if (!provider.capabilities().contains(GameProvider.Capability.DETAILS)) return -1;
        Instant now = clock.instant();
        if (pausedUntil != null && now.isBefore(pausedUntil)) return -1;
        var rows = references.bulkDetailCandidates(BATCH);
        if (rows.isEmpty()) {
            if (updated + notFound > 0) LOG.info("Toplu detay bootstrap bitti: yazılan={} bulunamayan={}", updated, notFound);
            updated = 0;
            notFound = 0;
            pausedUntil = now.plus(IDLE_INTERVAL);
            return -1;
        }
        Map<String, GameProvider.Details> found;
        try {
            found = provider.bulkDetails(rows.stream().map(row -> row.getExternalId()).toList());
        } catch (ProviderException error) {
            pause(error, now);
            return -1;
        }
        consecutiveFailures = 0;
        return Objects.requireNonNullElse(transaction.execute(status -> apply(rows, found)), -1);
    }

    private int apply(List<GameExternalReferenceRepository.ClassificationRow> rows, Map<String, GameProvider.Details> found) {
        states.lockProvider(PROVIDER_CODE).orElseThrow();
        games.lockCatalogGames(PROVIDER_CODE, rows.stream().map(row -> row.getExternalId()).toList());
        Map<UUID, Game> loaded = games.findAllById(rows.stream().map(row -> row.getGameId()).toList()).stream()
                .collect(Collectors.toMap(Game::getId, Function.identity()));
        int processed = 0;
        for (var row : rows) {
            Game game = loaded.get(row.getGameId());
            // Aday seçimi ile kilit arasında başka bir yol (tek kayıt yenilemesi) denemiş olabilir: yeniden işlenmez.
            if (game == null || game.getStatus() != GameStatus.ACTIVE || game.getOrigin() != GameOrigin.EXTERNAL
                    || game.getGameType() != GameType.MAIN_GAME || game.getDetailsFetchedAt() != null
                    || game.getDetailsFailureCount() != 0 || game.getDetailsLastErrorCode() != null) continue;
            GameProvider.Details details = found.get(row.getExternalId());
            if (details == null) {
                refresh.markNotFound(game);
                notFound++;
            } else {
                refresh.applyFetched(game, details);
                updated++;
            }
            processed++;
        }
        return processed;
    }

    private void pause(ProviderException error, Instant now) {
        consecutiveFailures = Math.min(consecutiveFailures + 1, 6);
        Duration delay = retries.delay(error, Math.min(consecutiveFailures, 5));
        Duration pause = delay == null ? MAX_PAUSE : delay.multipliedBy(consecutiveFailures >= 2 ? 4L : 1L);
        if (pause.compareTo(Duration.ofMinutes(1)) < 0) pause = Duration.ofMinutes(1);
        if (pause.compareTo(MAX_PAUSE) > 0) pause = MAX_PAUSE;
        pausedUntil = now.plus(pause);
        LOG.warn("Toplu detay işlemi duraklatıldı: kod={} ardışıkHata={} devam={}", error.code(), consecutiveFailures, pausedUntil);
    }
}

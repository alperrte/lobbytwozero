package com.ltz.game.application.service;

import com.ltz.game.application.port.GameProvider;
import com.ltz.game.domain.entity.Game;
import com.ltz.game.domain.enums.GameOrigin;
import com.ltz.game.domain.enums.GameStatus;
import com.ltz.game.domain.enums.GameType;
import com.ltz.game.domain.exception.ProviderException;
import com.ltz.game.infrastructure.config.GameRefreshProperties;
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
    private final GameRefreshProperties properties;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private Instant pausedUntil;
    private int consecutiveFailures;
    private long updated;
    private long notFound;
    private Instant refreshPausedUntil;
    private int refreshFailures;
    private long refreshed;
    private long refreshedWithoutEvidence;

    /** Anlık yenileme hedefi (ipucu tahliyesi); toplu aday sorgusu satırıyla aynı arayüzü sunar. */
    private record Target(String externalId, UUID gameId) implements GameExternalReferenceRepository.ClassificationRow {
        @Override public String getExternalId() { return externalId; }
        @Override public UUID getGameId() { return gameId; }
    }

    public GameDetailBulkService(GameProvider provider, GameRepository games, GameExternalReferenceRepository references,
            GameProviderSyncStateRepository states, GameDetailRefreshService refresh, ProviderRetryPolicy retries,
            GameRefreshProperties properties, PlatformTransactionManager transactions, Clock clock) {
        this.provider = provider;
        this.games = games;
        this.references = references;
        this.states = states;
        this.refresh = refresh;
        this.retries = retries;
        this.properties = properties;
        this.transaction = new TransactionTemplate(transactions);
        this.clock = clock;
    }

    /** Yalnız testler için. */
    synchronized void resetForTests() {
        pausedUntil = null;
        consecutiveFailures = 0;
        updated = 0;
        notFound = 0;
        refreshPausedUntil = null;
        refreshFailures = 0;
        refreshed = 0;
        refreshedWithoutEvidence = 0;
    }

    /** Boşta kalındığında sıcak aralığın üçte biri kadar bekler (en az 1, en çok 5 dakika): sıcak oyunlar vaktinde yeniden sorulur. */
    private Duration refreshIdleInterval() {
        Duration third = properties.details().hot().dividedBy(3);
        if (third.compareTo(Duration.ofMinutes(1)) < 0) return Duration.ofMinutes(1);
        return third.compareTo(Duration.ofMinutes(5)) > 0 ? Duration.ofMinutes(5) : third;
    }

    /**
     * Detayı daha önce alınmış oyunların periyodik YENİLEMESİ (aynı toplu yol: 100 AppID / 2 IGDB isteği). Sıcak oyunlar ({@link GameRefreshProperties})
     * kısa, diğerleri uzun aralıkla yeniden sorulur. Sağlayıcı bir oyun için kanıt vermezse eski detay korunur. Sağlayıcı HATASI veri yazmaz: yenileme duraklar.
     *
     * @return bu adımda işlenen oyun sayısı; iş yoksa, duraklatılmışsa veya yetenek yoksa {@code -1}
     */
    public synchronized int refreshNextBatch() {
        if (!provider.capabilities().contains(GameProvider.Capability.DETAILS)) return -1;
        Instant now = clock.instant();
        if (refreshPausedUntil != null && now.isBefore(refreshPausedUntil)) return -1;
        var rows = references.detailRefreshBulkCandidates(now.minus(properties.details().hot()), now.minus(properties.details().other()),
                properties.hotPastDays(), properties.hotFutureDays(), BATCH);
        if (rows.isEmpty()) {
            if (refreshed + refreshedWithoutEvidence > 0) LOG.info("Toplu detay yenileme turu bitti: yenilenen={} kanıtsız={}", refreshed, refreshedWithoutEvidence);
            refreshed = 0;
            refreshedWithoutEvidence = 0;
            refreshPausedUntil = now.plus(refreshIdleInterval());
            return -1;
        }
        return fetchAndApplyRefresh(rows, now);
    }

    /**
     * Sayfası açılan oyunlar için hemen yenileme (ipucu tahliyesi): detayı daha önce başarıyla alınmış, Steam referanslı oyunlar tek toplu çağrıyla sorulur.
     *
     * @return işlenen oyun sayısı; duraklatılmışsa veya yenilenecek oyun yoksa {@code -1}
     */
    public synchronized int refreshNow(java.util.Collection<UUID> gameIds) {
        Instant now = clock.instant();
        if (gameIds.isEmpty() || !provider.capabilities().contains(GameProvider.Capability.DETAILS)
                || (refreshPausedUntil != null && now.isBefore(refreshPausedUntil))) return -1;
        List<GameExternalReferenceRepository.ClassificationRow> rows = references.findByGameIdIn(gameIds).stream()
                .filter(ref -> PROVIDER_CODE.equals(ref.getProvider()))
                .<GameExternalReferenceRepository.ClassificationRow>map(ref -> new Target(ref.getExternalId(), ref.getGame().getId())).limit(BATCH).toList();
        return rows.isEmpty() ? -1 : fetchAndApplyRefresh(rows, now);
    }

    private int fetchAndApplyRefresh(List<GameExternalReferenceRepository.ClassificationRow> rows, Instant now) {
        Map<String, GameProvider.Details> found;
        try {
            found = provider.bulkDetails(rows.stream().map(row -> row.getExternalId()).toList());
        } catch (ProviderException error) {
            refreshFailures = Math.min(refreshFailures + 1, 6);
            refreshPausedUntil = now.plus(pauseFor(error, refreshFailures));
            LOG.warn("Toplu detay yenilemesi duraklatıldı: kod={} ardışıkHata={} devam={}", error.code(), refreshFailures, refreshPausedUntil);
            return -1;
        }
        refreshFailures = 0;
        return Objects.requireNonNullElse(transaction.execute(status -> applyRefresh(rows, found)), -1);
    }

    private int applyRefresh(List<GameExternalReferenceRepository.ClassificationRow> rows, Map<String, GameProvider.Details> found) {
        states.lockProvider(PROVIDER_CODE).orElseThrow();
        games.lockCatalogGames(PROVIDER_CODE, rows.stream().map(row -> row.getExternalId()).toList());
        Map<UUID, Game> loaded = games.findAllById(rows.stream().map(row -> row.getGameId()).toList()).stream()
                .collect(Collectors.toMap(Game::getId, Function.identity()));
        Duration ttl = properties.details().other();
        int processed = 0;
        for (var row : rows) {
            Game game = loaded.get(row.getGameId());
            // Yalnız detayı hatasız alınmış oyun yenilenir; ilk alım ve hata yeniden denemeleri kendi yollarında kalır.
            if (game == null || game.getStatus() != GameStatus.ACTIVE || game.getOrigin() != GameOrigin.EXTERNAL
                    || game.getGameType() != GameType.MAIN_GAME || game.getDetailsFetchedAt() == null
                    || game.getDetailsFailureCount() != 0 || game.getDetailsLastErrorCode() != null) continue;
            GameProvider.Details details = found.get(row.getExternalId());
            if (details == null) {
                refresh.markRefreshChecked(game, ttl);
                refreshedWithoutEvidence++;
            } else {
                refresh.applyFetched(game, details, ttl);
                refreshed++;
            }
            processed++;
        }
        return processed;
    }

    /** Türü Steam mağaza türünden verilmiş kayıt: IGDB kanıtı yokluğu bu kayıt için hata sayılmaz. */
    private static boolean steamClassified(Game game) {
        var marker = game.getMetadataProvenance().get("GAME_TYPE");
        return marker != null && "STEAM".equals(marker.getSource());
    }

    private Duration pauseFor(ProviderException error, int failures) {
        Duration delay = retries.delay(error, Math.min(failures, 5));
        Duration pause = delay == null ? MAX_PAUSE : delay.multipliedBy(failures >= 2 ? 4L : 1L);
        if (pause.compareTo(Duration.ofMinutes(1)) < 0) pause = Duration.ofMinutes(1);
        if (pause.compareTo(MAX_PAUSE) > 0) pause = MAX_PAUSE;
        return pause;
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
            if (details == null && steamClassified(game)) {
                // IGDB bu Steam kaynaklı oyunu tanımıyor: hata değil; detay kaynağı sonradan tanırsa yenileme turu uygular.
                refresh.markRefreshChecked(game, properties.details().other());
                notFound++;
            } else if (details == null) {
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

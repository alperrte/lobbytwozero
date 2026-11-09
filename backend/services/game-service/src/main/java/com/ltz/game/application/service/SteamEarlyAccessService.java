package com.ltz.game.application.service;

import com.ltz.game.application.port.GameProvider;
import com.ltz.game.domain.entity.Game;
import com.ltz.game.domain.entity.GameMetadataProvenance;
import com.ltz.game.domain.enums.GameOrigin;
import com.ltz.game.domain.enums.GameStatus;
import com.ltz.game.domain.enums.GameType;
import com.ltz.game.domain.enums.MetadataDataState;
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
 * Oyunların erken erişim durumunu Steam'in toplu mağaza verisinden ({@code release.is_early_access}, 100 oyun/istek) alır.
 * IGDB bu bilgiyi güvenilir vermediği için tek kaynak Steam'dir. Kurallar:
 * <ul>
 *   <li>Değer yalnız Steam oyunu tanıdığında yazılır; tanımadığı oyun bilinmiyor (null) kalır, "erken erişim değil" varsayılmaz.</li>
 *   <li>Yönetici veya önceki elle girilen değer ezilmez: yalnız boş değer ya da önceki Steam gözlemiyle yazılmış değer güncellenir
 *       (böylece oyun erken erişimden çıkınca false olur).</li>
 *   <li>Her inceleme {@code EARLY_ACCESS} gözlemiyle işaretlenir; oyun 30 gün sonra yeniden sorulur. Sağlayıcı HATASI işaret yazmaz,
 *       tarama artan beklemeyle duraklar.</li>
 * </ul>
 */
@Service
public class SteamEarlyAccessService {
    static final int BATCH = 100;
    static final Duration RECHECK = Duration.ofDays(30);
    static final Duration IDLE_INTERVAL = Duration.ofMinutes(30);
    static final String MARKER = "EARLY_ACCESS";
    private static final String PROVIDER_CODE = "STEAM";
    private static final Duration MAX_PAUSE = Duration.ofHours(1);
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(SteamEarlyAccessService.class);

    private final GameProvider provider;
    private final GameRepository games;
    private final GameExternalReferenceRepository references;
    private final GameProviderSyncStateRepository states;
    private final MetadataProvenanceService provenance;
    private final ProviderRetryPolicy retries;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private Instant pausedUntil;
    private int consecutiveFailures;
    private long updated;
    private long unknown;

    public SteamEarlyAccessService(GameProvider provider, GameRepository games, GameExternalReferenceRepository references,
            GameProviderSyncStateRepository states, MetadataProvenanceService provenance, ProviderRetryPolicy retries,
            PlatformTransactionManager transactions, Clock clock) {
        this.provider = provider;
        this.games = games;
        this.references = references;
        this.states = states;
        this.provenance = provenance;
        this.retries = retries;
        this.transaction = new TransactionTemplate(transactions);
        this.clock = clock;
    }

    /** Yalnız testler için. */
    synchronized void resetForTests() {
        pausedUntil = null;
        consecutiveFailures = 0;
        updated = 0;
        unknown = 0;
    }

    /** @return bu adımda işlenen oyun sayısı; iş yoksa veya duraklatılmışsa {@code -1} */
    public synchronized int nextBatch() {
        Instant now = clock.instant();
        if (pausedUntil != null && now.isBefore(pausedUntil)) return -1;
        var rows = references.earlyAccessCandidates(now.minus(RECHECK), BATCH);
        if (rows.isEmpty()) {
            if (updated + unknown > 0) LOG.info("Steam erken erişim taraması tamamlandı: yazılan={} bilinmeyen={}", updated, unknown);
            updated = 0;
            unknown = 0;
            pausedUntil = now.plus(IDLE_INTERVAL);
            return -1;
        }
        Map<String, Boolean> found;
        try {
            found = provider.earlyAccess(rows.stream().map(row -> row.getExternalId()).toList());
        } catch (ProviderException error) {
            pause(error, now);
            return -1;
        }
        consecutiveFailures = 0;
        return Objects.requireNonNullElse(transaction.execute(status -> apply(rows, found)), -1);
    }

    private int apply(List<GameExternalReferenceRepository.ClassificationRow> rows, Map<String, Boolean> found) {
        states.lockProvider(PROVIDER_CODE).orElseThrow();
        games.lockCatalogGames(PROVIDER_CODE, rows.stream().map(row -> row.getExternalId()).toList());
        Map<UUID, Game> loaded = games.findAllById(rows.stream().map(row -> row.getGameId()).toList()).stream()
                .collect(Collectors.toMap(Game::getId, Function.identity()));
        int processed = 0;
        for (var row : rows) {
            Game game = loaded.get(row.getGameId());
            if (game == null || game.getStatus() != GameStatus.ACTIVE || game.getOrigin() != GameOrigin.EXTERNAL || game.getGameType() != GameType.MAIN_GAME) continue;
            Boolean flag = found.get(row.getExternalId());
            GameMetadataProvenance previous = game.getMetadataProvenance().get(MARKER);
            boolean managed = previous != null && previous.getDataState() == MetadataDataState.AVAILABLE;
            if (flag != null && (game.getEarlyAccess() == null || (managed && !flag.equals(game.getEarlyAccess())))) {
                game.setEarlyAccess(flag);
                game.setUpdatedAt(clock.instant());
                managed = true;
                updated++;
            } else if (flag == null && game.getEarlyAccess() == null) {
                unknown++;
            }
            provenance.recordObservation(game, MARKER, "STEAM", managed ? MetadataDataState.AVAILABLE : MetadataDataState.UNKNOWN, false);
            processed++;
        }
        return processed;
    }

    private void pause(ProviderException error, Instant now) {
        consecutiveFailures = Math.min(consecutiveFailures + 1, 6);
        Duration delay = retries.delay(error, Math.min(consecutiveFailures, 5));
        long factor = consecutiveFailures >= 2 ? 4L : 1L;
        Duration pause = delay == null ? MAX_PAUSE : delay.multipliedBy(factor);
        if (pause.compareTo(Duration.ofMinutes(1)) < 0) pause = Duration.ofMinutes(1);
        if (pause.compareTo(MAX_PAUSE) > 0) pause = MAX_PAUSE;
        pausedUntil = now.plus(pause);
        LOG.warn("Steam erken erişim taraması duraklatıldı: kod={} ardışıkHata={} devam={}", error.code(), consecutiveFailures, pausedUntil);
    }
}

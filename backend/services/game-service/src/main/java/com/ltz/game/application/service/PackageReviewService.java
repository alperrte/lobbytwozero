package com.ltz.game.application.service;

import com.ltz.game.application.port.GameProvider;
import com.ltz.game.domain.entity.Game;
import com.ltz.game.domain.enums.GameOrigin;
import com.ltz.game.domain.enums.GameStatus;
import com.ltz.game.domain.enums.GameType;
import com.ltz.game.domain.enums.MetadataDataState;
import com.ltz.game.domain.enums.OverridableField;
import com.ltz.game.domain.exception.ProviderException;
import com.ltz.game.infrastructure.persistence.repository.GameExternalReferenceRepository;
import com.ltz.game.infrastructure.persistence.repository.GameFieldOverrideRepository;
import com.ltz.game.infrastructure.persistence.repository.GameProviderSyncStateRepository;
import com.ltz.game.infrastructure.persistence.repository.GameRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PACKAGE olarak sınıflanmış Steam kayıtlarından gerçek DLC olanları DLC'ye yükseltir. Toplu varsayım yoktur: bir kayıt yalnız
 * şu kanıtların <b>hepsi</b> varsa DLC olur:
 * <ol>
 *   <li>Steam'in kendi kataloğunda (yalnız-DLC listesi) DLC olarak listelenir;</li>
 *   <li>IGDB {@code game_type} değeri 13'tür (pack/addon). Bundle (3) ve diğer türler asla DLC yapılmaz, Steam DLC dese bile;</li>
 *   <li>IGDB bir {@code parent_game} bildirir (ana oyunu olan içerik; yalnız paket/koleksiyon değil);</li>
 *   <li>kayıtta manuel kilit veya yönetici kaynaklı tür yoktur.</li>
 * </ol>
 * Çelişkili veya yetersiz kanıtta kayıt PACKAGE kalır. Game kimliği, dış referans ve diğer provenance korunur; yalnız
 * {@code game_type} ve {@code GAME_TYPE} gözlemi değişir. Her inceleme {@code PACKAGE_REVIEW} gözlemiyle işaretlenir (30 gün
 * sonra yeni kanıt için yeniden incelenir); sağlayıcı HATASI işaret yazmaz, kayıt aday kalır. Idempotenttir.
 */
@Service
public class PackageReviewService {
    static final int BATCH = 250;
    static final Duration RECHECK = Duration.ofDays(30);
    static final Duration DLC_SET_TTL = Duration.ofHours(6);
    static final Duration IDLE_INTERVAL = Duration.ofMinutes(30);
    static final int IGDB_PACK = 13;
    static final String MARKER = "PACKAGE_REVIEW";
    private static final String PROVIDER_CODE = "STEAM";
    private static final Duration MAX_PAUSE = Duration.ofHours(1);
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(PackageReviewService.class);

    /** Bir kaydın inceleme sonucu. */
    public enum Outcome { CONVERTED, KEPT_BUNDLE, KEPT_NOT_STEAM_DLC, KEPT_OTHER_TYPE, KEPT_NO_PARENT, KEPT_NO_EVIDENCE, SKIPPED_PROTECTED }

    private final GameProvider provider;
    private final GameRepository games;
    private final GameExternalReferenceRepository references;
    private final GameFieldOverrideRepository overrides;
    private final GameProviderSyncStateRepository states;
    private final MetadataProvenanceService provenance;
    private final ProviderRetryPolicy retries;
    private final TransactionTemplate transaction;
    private final Clock clock;

    private final Map<Outcome, Long> totals = new EnumMap<>(Outcome.class);
    private Set<String> steamDlc;
    private Instant steamDlcAt;
    private Instant pausedUntil;
    private int consecutiveFailures;

    public PackageReviewService(GameProvider provider, GameRepository games, GameExternalReferenceRepository references,
            GameFieldOverrideRepository overrides, GameProviderSyncStateRepository states, MetadataProvenanceService provenance,
            ProviderRetryPolicy retries, PlatformTransactionManager transactions, Clock clock) {
        this.provider = provider;
        this.games = games;
        this.references = references;
        this.overrides = overrides;
        this.states = states;
        this.provenance = provenance;
        this.retries = retries;
        this.transaction = new TransactionTemplate(transactions);
        this.clock = clock;
    }

    /** Bu süreçte (yeniden başlatmaya kadar) sonuç başına toplam; yalnız raporlama ve test içindir. */
    public synchronized Map<Outcome, Long> totals() { return new EnumMap<>(totals); }

    /** Yalnız testler için: bekleme ve önbellek durumunu sıfırlar. */
    synchronized void resetForTests() {
        totals.clear();
        steamDlc = null;
        steamDlcAt = null;
        pausedUntil = null;
        consecutiveFailures = 0;
    }

    /**
     * Bir inceleme adımı (en çok {@value #BATCH} kayıt).
     *
     * @return bu adımda incelenen kayıt sayısı; iş yoksa, duraklatılmışsa veya yetenek yoksa {@code -1}
     */
    public synchronized int reviewNextBatch() {
        if (!provider.capabilities().contains(GameProvider.Capability.ITEM_TYPE)) return -1;
        Instant now = clock.instant();
        if (pausedUntil != null && now.isBefore(pausedUntil)) return -1;
        var rows = references.packageReviewBatch(now.minus(RECHECK), BATCH);
        if (rows.isEmpty()) {
            pausedUntil = now.plus(IDLE_INTERVAL);
            return -1;
        }
        Set<String> dlc;
        Map<String, GameProvider.RelationEvidence> evidence;
        try {
            dlc = steamDlcIds(now);
            // IGDB yalnız Steam'in DLC dediği kayıtlar için sorulur; diğerleri kanıt gerektirmeden PACKAGE kalır.
            List<String> candidates = rows.stream().map(row -> row.getExternalId()).filter(dlc::contains).toList();
            evidence = candidates.isEmpty() ? Map.of() : provider.relationEvidence(candidates);
        } catch (ProviderException error) {
            pause(error, now);
            return -1;
        }
        consecutiveFailures = 0;
        Integer reviewed = transaction.execute(status -> apply(rows, dlc, evidence, now));
        return reviewed == null ? -1 : reviewed;
    }

    private Set<String> steamDlcIds(Instant now) {
        if (steamDlc == null || steamDlcAt == null || now.isAfter(steamDlcAt.plus(DLC_SET_TTL))) {
            steamDlc = provider.catalogDlcIds();
            steamDlcAt = now;
            LOG.info("Steam DLC listesi alındı: {} kayıt", steamDlc.size());
        }
        return steamDlc;
    }

    private int apply(List<GameExternalReferenceRepository.ClassificationRow> rows, Set<String> dlc,
            Map<String, GameProvider.RelationEvidence> evidence, Instant now) {
        states.lockProvider(PROVIDER_CODE).orElseThrow();
        List<String> externalIds = rows.stream().map(row -> row.getExternalId()).toList();
        games.lockCatalogGames(PROVIDER_CODE, externalIds);
        Map<String, Set<OverridableField>> locked = new HashMap<>();
        overrides.lockedFields(PROVIDER_CODE, externalIds).forEach(row ->
                locked.computeIfAbsent(row.getExternalId(), key -> EnumSet.noneOf(OverridableField.class)).add(row.getField()));
        Map<UUID, Game> loaded = games.findAllById(rows.stream().map(row -> row.getGameId()).toList()).stream()
                .collect(Collectors.toMap(Game::getId, Function.identity()));
        Map<Outcome, Integer> batch = new EnumMap<>(Outcome.class);
        int reviewed = 0;
        for (var row : rows) {
            Game game = loaded.get(row.getGameId());
            if (game == null || game.getGameType() != GameType.PACKAGE || game.getStatus() != GameStatus.ACTIVE
                    || game.getOrigin() != GameOrigin.EXTERNAL) continue;
            Outcome outcome = decide(game, row.getExternalId(), locked.getOrDefault(row.getExternalId(), Set.of()), dlc, evidence.get(row.getExternalId()));
            if (outcome == Outcome.CONVERTED) {
                game.setGameType(GameType.DLC);
                provenance.recordObservation(game, "GAME_TYPE", provider.detailSource(), MetadataDataState.AVAILABLE, false);
                game.setUpdatedAt(now);
            }
            provenance.recordObservation(game, MARKER, provider.detailSource(),
                    outcome == Outcome.CONVERTED ? MetadataDataState.AVAILABLE : MetadataDataState.UNKNOWN, false);
            batch.merge(outcome, 1, Integer::sum);
            totals.merge(outcome, 1L, Long::sum);
            reviewed++;
        }
        LOG.info("PACKAGE incelemesi: incelenen={} sonuçlar={}", reviewed, batch);
        return reviewed;
    }

    /** Karar saf kanıt kurallarıdır; ad, popülerlik veya appid kullanılmaz. */
    static Outcome decide(Game game, String externalId, Set<OverridableField> locked, Set<String> steamDlc, GameProvider.RelationEvidence igdb) {
        var typeProvenance = game.getMetadataProvenance().get("GAME_TYPE");
        if (locked.contains(OverridableField.GAME_TYPE) || (typeProvenance != null && "ADMIN".equals(typeProvenance.getSource()))) {
            return Outcome.SKIPPED_PROTECTED;
        }
        if (!steamDlc.contains(externalId)) return Outcome.KEPT_NOT_STEAM_DLC;
        if (igdb == null || igdb.providerGameType() == null) return Outcome.KEPT_NO_EVIDENCE;
        if (igdb.providerGameType() == 3) return Outcome.KEPT_BUNDLE;
        if (igdb.providerGameType() != IGDB_PACK) return Outcome.KEPT_OTHER_TYPE;
        if (igdb.parentState() == GameProvider.ParentState.NONE) return Outcome.KEPT_NO_PARENT;
        return Outcome.CONVERTED;
    }

    private void pause(ProviderException error, Instant now) {
        consecutiveFailures = Math.min(consecutiveFailures + 1, 6);
        Duration delay = retries.delay(error, Math.min(consecutiveFailures, 5));
        Duration pause = delay == null ? MAX_PAUSE : delay.multipliedBy(consecutiveFailures >= 2 ? 4L : 1L);
        if (pause.compareTo(Duration.ofMinutes(1)) < 0) pause = Duration.ofMinutes(1);
        if (pause.compareTo(MAX_PAUSE) > 0) pause = MAX_PAUSE;
        pausedUntil = now.plus(pause);
        LOG.warn("PACKAGE incelemesi duraklatıldı: kod={} ardışıkHata={} devam={}", error.code(), consecutiveFailures, pausedUntil);
    }
}

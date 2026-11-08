package com.ltz.game.application.service;

import com.ltz.game.application.port.GameProvider;
import com.ltz.game.domain.entity.Game;
import com.ltz.game.domain.entity.GameLanguageSupport;
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
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Tüm ana oyunlar için Steam'in toplu mağaza verisini (100 oyun/istek, resmî Web API) alır: 16:9 başlık görseli, geliştirici, yayıncı ve dil desteği.
 * Tek tek mağaza sayfası sorgusunun (dakikada ~35) yerini alır: 144 bin oyun günler yerine yarım saatte tamamlanır. Kurallar:
 * <ul>
 *   <li>Küçük resim çerçevesi 16:9'dur; Steam başlık görseli IGDB'nin dikey kapağının yerine geçer ({@link GameCoverWriter}; yönetici kapağı ezilmez).</li>
 *   <li>Geliştirici, yayıncı ve dil yalnız BOŞKEN doldurulur; dolu veri, yönetici düzeltmesi ve manuel kilit ezilmez.</li>
 *   <li>Her inceleme {@code STEAM_ITEMS} gözlemiyle işaretlenir (veri geldiyse AVAILABLE, hiç alan yoksa UNKNOWN; 30 gün sonra yeniden bakılır).
 *       Sağlayıcı HATASI işaret yazmaz, tarama duraklar.</li>
 * </ul>
 */
@Service
public class SteamStoreItemService {
    static final int BATCH = 100;
    static final Duration RECHECK = Duration.ofDays(30);
    static final Duration IDLE_INTERVAL = Duration.ofMinutes(30);
    static final String MARKER = "STEAM_ITEMS";
    private static final String PROVIDER_CODE = "STEAM";
    private static final Duration MAX_PAUSE = Duration.ofHours(1);
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(SteamStoreItemService.class);

    private final GameProvider provider;
    private final GameRepository games;
    private final GameExternalReferenceRepository references;
    private final GameFieldOverrideRepository overrides;
    private final GameProviderSyncStateRepository states;
    private final MetadataProvenanceService provenance;
    private final ProviderRetryPolicy retries;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private Instant pausedUntil;
    private int consecutiveFailures;
    private long updated;
    private long withoutData;

    public SteamStoreItemService(GameProvider provider, GameRepository games, GameExternalReferenceRepository references,
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

    /** Yalnız testler için. */
    synchronized void resetForTests() {
        pausedUntil = null;
        consecutiveFailures = 0;
        updated = 0;
        withoutData = 0;
    }

    /** @return bu adımda işlenen oyun sayısı; iş yoksa veya duraklatılmışsa {@code -1} */
    public synchronized int nextBatch() {
        Instant now = clock.instant();
        if (pausedUntil != null && now.isBefore(pausedUntil)) return -1;
        var rows = references.storeItemCandidates(now.minus(RECHECK), BATCH);
        if (rows.isEmpty()) {
            if (updated + withoutData > 0) LOG.info("Steam toplu mağaza öğeleri tamamlandı: doldurulan={} veri-olmayan={}", updated, withoutData);
            updated = 0;
            withoutData = 0;
            pausedUntil = now.plus(IDLE_INTERVAL);
            return -1;
        }
        Map<String, GameProvider.StoreItem> found;
        try {
            found = provider.storeItems(rows.stream().map(row -> row.getExternalId()).toList());
        } catch (ProviderException error) {
            pause(error, now);
            return -1;
        }
        consecutiveFailures = 0;
        return Objects.requireNonNullElse(transaction.execute(status -> apply(rows, found)), -1);
    }

    private int apply(List<GameExternalReferenceRepository.ClassificationRow> rows, Map<String, GameProvider.StoreItem> found) {
        states.lockProvider(PROVIDER_CODE).orElseThrow();
        games.lockCatalogGames(PROVIDER_CODE, rows.stream().map(row -> row.getExternalId()).toList());
        Map<UUID, Game> loaded = games.findAllById(rows.stream().map(row -> row.getGameId()).toList()).stream()
                .collect(Collectors.toMap(Game::getId, Function.identity()));
        int processed = 0;
        for (var row : rows) {
            Game game = loaded.get(row.getGameId());
            if (game == null || game.getStatus() != GameStatus.ACTIVE || game.getOrigin() != GameOrigin.EXTERNAL || game.getGameType() != GameType.MAIN_GAME) continue;
            GameProvider.StoreItem item = found.get(row.getExternalId());
            boolean changed = item != null && merge(game, item);
            provenance.recordObservation(game, MARKER, "STEAM", item == null ? MetadataDataState.UNKNOWN : MetadataDataState.AVAILABLE, false);
            if (changed) {
                game.setUpdatedAt(clock.instant());
                updated++;
            } else if (item == null) {
                withoutData++;
            }
            processed++;
        }
        return processed;
    }

    private boolean merge(Game game, GameProvider.StoreItem item) {
        Set<OverridableField> locked = EnumSet.noneOf(OverridableField.class);
        overrides.findByGameIdOrderByFieldAsc(game.getId()).forEach(override -> locked.add(override.getField()));
        boolean changed = false;
        if (GameCoverWriter.apply(game, item.headerImageUrl(), "STEAM")) {
            provenance.recordObservation(game, "COVER_URL", "STEAM", MetadataDataState.AVAILABLE, false);
            changed = true;
        }
        if (game.getDevelopers().isEmpty() && !item.developers().isEmpty() && !locked.contains(OverridableField.DEVELOPERS)) {
            game.getDevelopers().addAll(item.developers());
            provenance.recordObservation(game, "DEVELOPERS", "STEAM", MetadataDataState.AVAILABLE, false);
            changed = true;
        }
        if (game.getPublishers().isEmpty() && !item.publishers().isEmpty() && !locked.contains(OverridableField.PUBLISHERS)) {
            game.getPublishers().addAll(item.publishers());
            provenance.recordObservation(game, "PUBLISHERS", "STEAM", MetadataDataState.AVAILABLE, false);
            changed = true;
        }
        if (game.getLanguageDataState() != MetadataDataState.AVAILABLE && !item.languages().isEmpty() && !locked.contains(OverridableField.LOCALIZATION)) {
            Set<GameLanguageSupport> supports = new HashSet<>();
            Set<String> codes = new TreeSet<>();
            for (GameProvider.LanguageSupport language : item.languages()) {
                var value = new GameLanguageSupport();
                value.setLanguageCode(language.languageCode());
                value.setInterfaceSupport(language.interfaceSupport());
                value.setAudioSupport(language.audioSupport());
                value.setSubtitleSupport(language.subtitleSupport());
                supports.add(value);
                codes.add(language.languageCode());
            }
            game.getLanguageSupport().clear();
            game.getLanguageSupport().addAll(supports);
            game.setLanguageDataState(MetadataDataState.AVAILABLE);
            provenance.recordObservation(game, "LOCALIZATION", "STEAM", MetadataDataState.AVAILABLE, false);
            if (game.getLanguages().isEmpty()) {
                game.getLanguages().addAll(codes);
                provenance.recordObservation(game, "LANGUAGES", "STEAM", MetadataDataState.AVAILABLE, false);
            }
            changed = true;
        }
        return changed;
    }

    private void pause(ProviderException error, Instant now) {
        consecutiveFailures = Math.min(consecutiveFailures + 1, 6);
        Duration delay = retries.delay(error, Math.min(consecutiveFailures, 5));
        long factor = consecutiveFailures >= 2 ? 4L : 1L;
        Duration pause = delay == null ? MAX_PAUSE : delay.multipliedBy(factor);
        if (pause.compareTo(Duration.ofMinutes(1)) < 0) pause = Duration.ofMinutes(1);
        if (pause.compareTo(MAX_PAUSE) > 0) pause = MAX_PAUSE;
        pausedUntil = now.plus(pause);
        LOG.warn("Steam toplu mağaza öğeleri duraklatıldı: kod={} ardışıkHata={} devam={}", error.code(), consecutiveFailures, pausedUntil);
    }
}

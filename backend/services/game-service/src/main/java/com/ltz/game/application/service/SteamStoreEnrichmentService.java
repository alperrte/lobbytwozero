package com.ltz.game.application.service;

import com.ltz.game.application.port.GameProvider;
import com.ltz.game.application.port.StoreDetailSource;
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
import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * IGDB'nin vermediği veya eksik bıraktığı alanları Steam mağaza sayfasının herkese açık verisiyle tamamlar: başlık görseli (kapak),
 * geliştirici, yayıncı ve dil desteği. Yalnız BOŞ alanlar doldurulur; yönetici verisi, manuel kilitler ve mevcut doluluk ezilmez
 * (tek istisna: Steam başlık görseli IGDB kapağının yerine geçer, bkz. {@link GameCoverWriter}). Her çağrı tek AppID işler ve Steam'in
 * hız sınırına uygun aralıklıdır (istemci uygular). Her inceleme {@code STEAM_STORE} gözlemiyle işaretlenir (30 gün sonra yeniden
 * bakılır); sağlayıcı HATASI işaret yazmaz, tarama duraklar. Öncelik: editör seçimi, sonra yayımlanmış oyunlar (yeni önce).
 */
@Service
public class SteamStoreEnrichmentService {
    static final Duration RECHECK = Duration.ofDays(30);
    static final Duration IDLE_INTERVAL = Duration.ofMinutes(30);
    static final int QUEUE_SIZE = 100;
    static final String MARKER = "STEAM_STORE";
    private static final String PROVIDER_CODE = "STEAM";
    private static final Duration MAX_PAUSE = Duration.ofHours(1);
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(SteamStoreEnrichmentService.class);

    private final StoreDetailSource store;
    private final GameRepository games;
    private final GameExternalReferenceRepository references;
    private final GameFieldOverrideRepository overrides;
    private final GameProviderSyncStateRepository states;
    private final MetadataProvenanceService provenance;
    private final ProviderRetryPolicy retries;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final ArrayDeque<GameExternalReferenceRepository.ClassificationRow> queue = new ArrayDeque<>();
    private Instant pausedUntil;
    private int consecutiveFailures;
    private long enriched;
    private long empty;

    public SteamStoreEnrichmentService(StoreDetailSource store, GameRepository games, GameExternalReferenceRepository references,
            GameFieldOverrideRepository overrides, GameProviderSyncStateRepository states, MetadataProvenanceService provenance,
            ProviderRetryPolicy retries, PlatformTransactionManager transactions, Clock clock) {
        this.store = store;
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
        queue.clear();
        pausedUntil = null;
        consecutiveFailures = 0;
        enriched = 0;
        empty = 0;
    }

    /**
     * Bir AppID için mağaza verisini alır ve boş alanları doldurur.
     *
     * @return 1 işlendi; iş yoksa veya duraklatılmışsa {@code -1}
     */
    public synchronized int enrichNext() {
        Instant now = clock.instant();
        if (pausedUntil != null && now.isBefore(pausedUntil)) return -1;
        if (queue.isEmpty()) {
            queue.addAll(references.storeEnrichmentCandidates(now.minus(RECHECK), QUEUE_SIZE));
            if (queue.isEmpty()) {
                if (enriched + empty > 0) LOG.info("Steam mağaza tamamlama bitti: doldurulan={} veri-yok={}", enriched, empty);
                enriched = 0;
                empty = 0;
                pausedUntil = now.plus(IDLE_INTERVAL);
                return -1;
            }
        }
        var row = queue.peekFirst();
        Optional<StoreDetailSource.StoreDetails> details;
        try {
            details = store.details(row.getExternalId());
        } catch (ProviderException error) {
            if ("INVALID_PROVIDER_REQUEST".equals(error.code())) {
                queue.pollFirst();
                return 0;
            }
            pause(error, now);
            return -1;
        }
        queue.pollFirst();
        consecutiveFailures = 0;
        transaction.executeWithoutResult(status -> apply(row, details, now));
        return 1;
    }

    private void apply(GameExternalReferenceRepository.ClassificationRow row, Optional<StoreDetailSource.StoreDetails> details, Instant now) {
        states.lockProvider(PROVIDER_CODE).orElseThrow();
        games.lockCatalogGames(PROVIDER_CODE, java.util.List.of(row.getExternalId()));
        Game game = games.findById(row.getGameId()).orElse(null);
        if (game == null || game.getStatus() != GameStatus.ACTIVE || game.getOrigin() != GameOrigin.EXTERNAL || game.getGameType() != GameType.MAIN_GAME) return;
        Set<OverridableField> locked = EnumSet.noneOf(OverridableField.class);
        overrides.findByGameIdOrderByFieldAsc(game.getId()).forEach(override -> locked.add(override.getField()));
        boolean changed = details.isPresent() && merge(game, details.get(), locked);
        provenance.recordObservation(game, MARKER, store.source(),
                details.isEmpty() ? MetadataDataState.UNKNOWN : changed ? MetadataDataState.AVAILABLE : MetadataDataState.EMPTY, false);
        if (changed) {
            game.setUpdatedAt(now);
            enriched++;
        } else {
            empty++;
        }
    }

    private boolean merge(Game game, StoreDetailSource.StoreDetails details, Set<OverridableField> locked) {
        boolean changed = false;
        if (GameCoverWriter.apply(game, details.headerImageUrl(), store.source())) {
            provenance.recordObservation(game, "COVER_URL", store.source(), MetadataDataState.AVAILABLE, false);
            changed = true;
        }
        if (game.getDevelopers().isEmpty() && !details.developers().isEmpty() && !locked.contains(OverridableField.DEVELOPERS)) {
            game.getDevelopers().addAll(details.developers());
            provenance.recordObservation(game, "DEVELOPERS", store.source(), MetadataDataState.AVAILABLE, false);
            changed = true;
        }
        if (game.getPublishers().isEmpty() && !details.publishers().isEmpty() && !locked.contains(OverridableField.PUBLISHERS)) {
            game.getPublishers().addAll(details.publishers());
            provenance.recordObservation(game, "PUBLISHERS", store.source(), MetadataDataState.AVAILABLE, false);
            changed = true;
        }
        if (game.getLanguageDataState() != MetadataDataState.AVAILABLE && !details.languages().isEmpty() && !locked.contains(OverridableField.LOCALIZATION)) {
            Set<GameLanguageSupport> supports = new HashSet<>();
            Set<String> codes = new TreeSet<>();
            for (GameProvider.LanguageSupport language : details.languages()) {
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
            provenance.recordObservation(game, "LOCALIZATION", store.source(), MetadataDataState.AVAILABLE, false);
            if (game.getLanguages().isEmpty()) {
                game.getLanguages().addAll(codes);
                provenance.recordObservation(game, "LANGUAGES", store.source(), MetadataDataState.AVAILABLE, false);
            }
            changed = true;
        }
        return changed;
    }

    private void pause(ProviderException error, Instant now) {
        consecutiveFailures = Math.min(consecutiveFailures + 1, 6);
        Duration delay = retries.delay(error, Math.min(consecutiveFailures, 5));
        Duration pause = delay == null ? MAX_PAUSE : delay.multipliedBy(consecutiveFailures >= 2 ? 4L : 1L);
        if (pause.compareTo(Duration.ofMinutes(1)) < 0) pause = Duration.ofMinutes(1);
        if (pause.compareTo(MAX_PAUSE) > 0) pause = MAX_PAUSE;
        pausedUntil = now.plus(pause);
        LOG.warn("Steam mağaza tamamlama duraklatıldı: kod={} ardışıkHata={} devam={}", error.code(), consecutiveFailures, pausedUntil);
    }
}

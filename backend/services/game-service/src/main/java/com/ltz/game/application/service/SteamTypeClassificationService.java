package com.ltz.game.application.service;

import com.ltz.game.application.port.GameProvider;
import com.ltz.game.application.port.GameProvider.StoreType;
import com.ltz.game.domain.entity.CatalogTerm;
import com.ltz.game.domain.entity.Game;
import com.ltz.game.domain.enums.GameOrigin;
import com.ltz.game.domain.enums.GameStatus;
import com.ltz.game.domain.enums.GameType;
import com.ltz.game.domain.enums.MetadataDataState;
import com.ltz.game.domain.enums.OverridableField;
import com.ltz.game.domain.enums.TermKind;
import com.ltz.game.domain.exception.ProviderException;
import com.ltz.game.infrastructure.persistence.repository.CatalogTermRepository;
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
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Yeni ve IGDB'nin tanımadığı Steam kayıtlarının türünü Steam'in kendi mağaza türünden belirler (yedek kanıt): {@code type=0} ana oyun,
 * {@code type=4} DLC; başka değerler, Steam'in tanımadığı ve görünmez ({@code visible=false}) kayıtlar UNKNOWN kalır. IGDB'yi BEKLEMEZ; IGDB
 * sonra kanıt verirse tür, meta veri ve terimler IGDB'ye geçer ({@link CatalogClassificationService}). Tür kanıt sırası: yönetici kilidi
 * (hiç dokunulmaz) &gt; IGDB &gt; bu servis.
 * <p>
 * Kurallar:
 * <ul>
 *   <li>Her inceleme {@code STEAM_TYPE} gözlemiyle işaretlenir; UNKNOWN kalan kayıt {@link #RECHECK} sonra yeniden sorulur, sınıflanan kayıt aday
 *       olmaz. Sağlayıcı HATASI gözlem yazmaz: tarama artan beklemeyle duraklar (yanlış "tanınmadı" yok).</li>
 *   <li>Tür kaynağı STEAM yazılır ({@code GAME_TYPE}); çıkış tarihi yalnız boşsa ve kilit yoksa; tür/etiket/kategori/platform terimleri yalnız o
 *       türde terimi yoksa ve yalnız katalogdaki MEVCUT kodlarla ({@link SteamTermMapper}; terim uydurulmaz).</li>
 *   <li>DLC'nin ana oyunu Steam'in {@code related_items.parent_appid} bilgisinden {@link DlcParentService} tarafından toplu bağlanır.</li>
 * </ul>
 */
@Service
public class SteamTypeClassificationService {
    static final int BATCH = 100;
    static final String MARKER = "STEAM_TYPE";
    /** Tanınmayan/desteklenmeyen türdeki kaydın yeniden sorulma aralığı (Steam sonradan tanıyabilir / türü değişebilir). */
    static final Duration RECHECK = Duration.ofDays(7);
    private static final String PROVIDER_CODE = "STEAM";
    private static final Duration MAX_PAUSE = Duration.ofHours(1);
    private static final Duration TAG_NAMES_TTL = Duration.ofHours(24);
    private static final Map<TermKind, String> TERM_FIELDS = Map.of(TermKind.GENRE, "GENRES", TermKind.TAG, "TAGS",
            TermKind.CATEGORY, "CATEGORIES", TermKind.PLATFORM, "PLATFORMS");
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(SteamTypeClassificationService.class);

    /** Bir oyunun Steam AppID'si. */
    private record Target(String externalId, UUID gameId) { }

    private final GameProvider provider;
    private final GameRepository games;
    private final GameExternalReferenceRepository references;
    private final GameFieldOverrideRepository overrides;
    private final GameProviderSyncStateRepository states;
    private final CatalogTermRepository termRepository;
    private final GameTypeClassifier types;
    private final SteamTermMapper mapper;
    private final MetadataProvenanceService provenance;
    private final ProviderRetryPolicy retries;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final boolean enabled;
    private Instant pausedUntil;
    private int consecutiveFailures;
    private Map<Integer, String> tagNames = Map.of();
    private Instant tagNamesFetchedAt = Instant.MIN;
    private long classified;
    private long unclassified;

    public SteamTypeClassificationService(GameProvider provider, GameRepository games, GameExternalReferenceRepository references,
            GameFieldOverrideRepository overrides, GameProviderSyncStateRepository states, CatalogTermRepository termRepository,
            GameTypeClassifier types, SteamTermMapper mapper, MetadataProvenanceService provenance, ProviderRetryPolicy retries,
            PlatformTransactionManager transactions, Clock clock,
            @org.springframework.beans.factory.annotation.Value("${ltz.game.catalog.steam-type.enabled:true}") boolean enabled) {
        this.provider = provider;
        this.games = games;
        this.references = references;
        this.overrides = overrides;
        this.states = states;
        this.termRepository = termRepository;
        this.types = types;
        this.mapper = mapper;
        this.provenance = provenance;
        this.retries = retries;
        this.transaction = new TransactionTemplate(transactions);
        this.clock = clock;
        this.enabled = enabled;
    }

    /** Yalnız testler için. */
    synchronized void resetForTests() {
        pausedUntil = null;
        consecutiveFailures = 0;
        tagNames = Map.of();
        tagNamesFetchedAt = Instant.MIN;
        classified = 0;
        unclassified = 0;
    }

    /** @return bu adımda işlenen kayıt sayısı; iş yoksa, kapalıysa veya duraklatılmışsa {@code -1} */
    public synchronized int nextBatch() {
        Instant now = clock.instant();
        if (!enabled || (pausedUntil != null && now.isBefore(pausedUntil))) return -1;
        List<Target> rows = references.steamTypeCandidates(now.minus(RECHECK), BATCH).stream()
                .map(row -> new Target(row.getExternalId(), row.getGameId())).toList();
        if (rows.isEmpty()) {
            if (classified + unclassified > 0) LOG.info("Steam tür yedeği tamamlandı: sınıflanan={} bilinmeyen={}", classified, unclassified);
            classified = 0;
            unclassified = 0;
            pausedUntil = now.plus(Duration.ofMinutes(1));
            return -1;
        }
        Map<String, StoreType> found;
        Map<Integer, String> names;
        try {
            found = provider.storeTypes(rows.stream().map(Target::externalId).toList());
            names = tagNames(now);
        } catch (ProviderException error) {
            pause(error, now);
            return -1;
        }
        consecutiveFailures = 0;
        return Objects.requireNonNullElse(transaction.execute(status -> apply(rows, found, names, now)), -1);
    }

    /** Etiket adları günde bir alınır; alınamazsa tüm tur duraklar (terimsiz sınıflama yapılmaz, işaret yazılmaz). */
    private Map<Integer, String> tagNames(Instant now) {
        if (tagNames.isEmpty() || now.isAfter(tagNamesFetchedAt.plus(TAG_NAMES_TTL))) {
            tagNames = provider.storeTagNames();
            tagNamesFetchedAt = now;
        }
        return tagNames;
    }

    private int apply(List<Target> rows, Map<String, StoreType> found, Map<Integer, String> names, Instant now) {
        states.lockProvider(PROVIDER_CODE).orElseThrow();
        List<String> externalIds = rows.stream().map(Target::externalId).toList();
        games.lockCatalogGames(PROVIDER_CODE, externalIds);
        Map<String, Set<OverridableField>> locked = new HashMap<>();
        overrides.lockedFields(PROVIDER_CODE, externalIds).forEach(row ->
                locked.computeIfAbsent(row.getExternalId(), key -> EnumSet.noneOf(OverridableField.class)).add(row.getField()));
        Map<UUID, Game> loaded = games.findAllById(rows.stream().map(Target::gameId).toList()).stream()
                .collect(Collectors.toMap(Game::getId, Function.identity()));
        Map<String, CatalogTerm> termCache = new HashMap<>();
        int processed = 0;
        for (Target row : rows) {
            Game game = loaded.get(row.gameId());
            Set<OverridableField> lockedFields = locked.getOrDefault(row.externalId(), Set.of());
            if (game == null || game.getStatus() != GameStatus.ACTIVE || game.getOrigin() != GameOrigin.EXTERNAL
                    || game.getGameType() != GameType.UNKNOWN || game.isDelisted() || lockedFields.contains(OverridableField.GAME_TYPE)) continue;
            StoreType store = found.get(row.externalId());
            GameType type = store == null || !store.visible() ? GameType.UNKNOWN : types.fromSteamStoreType(store.type());
            if (type == GameType.UNKNOWN) {
                provenance.recordObservation(game, MARKER, PROVIDER_CODE, MetadataDataState.UNKNOWN, false);
                unclassified++;
            } else {
                classify(game, type, store, names, lockedFields, termCache, now);
                classified++;
            }
            processed++;
        }
        return processed;
    }

    private void classify(Game game, GameType type, StoreType store, Map<Integer, String> names, Set<OverridableField> locked, Map<String, CatalogTerm> termCache,
            Instant now) {
        game.setGameType(type);
        provenance.recordObservation(game, "GAME_TYPE", PROVIDER_CODE, MetadataDataState.AVAILABLE, false);
        provenance.recordObservation(game, MARKER, PROVIDER_CODE, MetadataDataState.AVAILABLE, false);
        if (store.releasedOn() != null && game.getReleaseDate() == null && !locked.contains(OverridableField.RELEASE_DATE)) {
            game.setReleaseDate(store.releasedOn());
            provenance.recordObservation(game, "RELEASE_DATE", PROVIDER_CODE, MetadataDataState.AVAILABLE, false);
        }
        SteamTermMapper.Terms terms = mapper.map(store, names);
        Map<TermKind, Set<String>> codes = new EnumMap<>(TermKind.class);
        terms.byKind().forEach(codes::put);
        codes.forEach((kind, kindCodes) -> attachTerms(game, kind, kindCodes, termCache));
        game.setUpdatedAt(now);
    }

    /** Yalnız o türde hiç terimi olmayan oyuna ve yalnız katalogda zaten bulunan kodlarla yazar (eksik kod atlanır). */
    private void attachTerms(Game game, TermKind kind, Set<String> codes, Map<String, CatalogTerm> termCache) {
        if (codes.isEmpty() || game.getTerms().stream().anyMatch(term -> term.getKind() == kind)) return;
        boolean attached = false;
        for (String code : codes) {
            CatalogTerm term = termCache.computeIfAbsent(kind + ":" + code, key -> termRepository.findByKindAndCode(kind, code).orElse(null));
            if (term != null) {
                game.getTerms().add(term);
                attached = true;
            }
        }
        if (attached) provenance.recordObservation(game, TERM_FIELDS.get(kind), PROVIDER_CODE, MetadataDataState.AVAILABLE, false);
    }

    private void pause(ProviderException error, Instant now) {
        consecutiveFailures = Math.min(consecutiveFailures + 1, 6);
        Duration delay = retries.delay(error, Math.min(consecutiveFailures, 5));
        long factor = consecutiveFailures >= 2 ? 4L : 1L;
        Duration pause = delay == null ? MAX_PAUSE : delay.multipliedBy(factor);
        if (pause.compareTo(Duration.ofMinutes(1)) < 0) pause = Duration.ofMinutes(1);
        if (pause.compareTo(MAX_PAUSE) > 0) pause = MAX_PAUSE;
        pausedUntil = now.plus(pause);
        LOG.warn("Steam tür yedeği duraklatıldı: kod={} ardışıkHata={} devam={}", error.code(), consecutiveFailures, pausedUntil);
    }
}

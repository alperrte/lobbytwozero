package com.ltz.game.application.service;

import com.ltz.game.application.port.GameProvider;
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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Steam kataloğundaki kayıtları sağlayıcının toplu kanıtıyla (IGDB game_type, first_release_date, genres, platforms, game_modes,
 * themes) sınıflandırır ve zenginleştirir. Steam günlük senkronundan bağımsız, kendi zamanlayıcı turlarıyla çalışır.
 * <p>
 * Sınıflandırma ({@link #classifyNextBatch}): türü UNKNOWN kayıtlar. Tür yalnız sağlayıcı kanıtından gelir (ad, appid, popülerlik
 * veya toplu varsayım yok). Kanıt yoksa, eşleme belirsizse ya da tür hedef kapsam dışıysa kayıt UNKNOWN kalır ve provenance'a
 * {@code GAME_TYPE/UNKNOWN} gözlemi yazılır; kayıt yeniden sorulmaz: son 30 günde ilk kez görülen kayıt 1 gün, eski kayıt 7 gün
 * (mevcut tabloyla, migration yok). Sağlayıcı HATASI gözlem yazmaz: tarama artan beklemeyle duraklar ve kayıt aday kalır, böylece
 * geçici hata gerçek "eşleşme yok" sonucundan ayrılır ve hiçbir uygun kayıt bekleme politikası yüzünden işlenmeden kalmaz.
 * <p>
 * Zenginleştirme ({@link #enrichNextBatch}): türü doğrulanmış (MAIN_GAME/DLC) ama IGDB metadata'sı hiç denenmemiş kayıtlar için
 * çıkış tarihi (yalnız boşsa), tür, platform, kategori (oyun modu) ve etiket (tema) terimleri (yalnız o türde terimi yoksa). Bir kez
 * denenen kayıt {@code IGDB_METADATA} gözlemiyle işaretlenir ve tekrar sorulmaz. Yönetici verisi ve manuel kilitler ezilmez.
 * <p>
 * Sıra: AppID gerçek çıkış tarihi veya popülerlik kanıtı değildir, yalnız işleme önceliğidir. Adımlar dönüşümlü olarak en yeni
 * (azalan) ve en eski (artan) uçtan ilerler: güncel kayıtlar erken işlenir, eski kayıtlar da sürekli ilerler. İlerleme AppID
 * imleçleridir (bellekte) ve işaretler PostgreSQL'dedir: yeniden başlatma taramayı baştan yapar ama işlenmiş kayıtlar aday olmadığından
 * kaldığı yerden devam eder; işlem idempotenttir.
 * <p>
 * Büyük birikim anahtarı: {@code game.catalog.classification.backlog-enabled} (varsayılan açık). Kapalıysa ve aday sayısı
 * {@link #DEFAULT_AUTO_LIMIT} üstündeyse sınıflandırma çalışmaz (günlük delta ölçeği her zaman çalışır); yeniden başlatma kendiliğinden
 * deneme grubu başlatmaz.
 */
@Service @RequiredArgsConstructor
public class CatalogClassificationService {
    static final int BATCH = 250;
    /** Turlar arası bekleme: bekleme süresi dolan ve yeni gelen kayıtlar bu aralıkla yakalanır; boş turun maliyeti tek sorgudur. */
    static final Duration PASS_INTERVAL = Duration.ofMinutes(30);
    /** IGDB'de kanıtı olmayan kaydın yeniden sorulma aralıkları (kademeli): yeni kayıt kısa, eski kayıt uzun. */
    static final Duration RECHECK_RECENT = Duration.ofDays(1);
    static final Duration RECHECK_AFTER = Duration.ofDays(7);
    /** "Yeni" kayıt: son bu süre içinde ilk kez görülen (games.created_at). */
    static final Duration RECENT_WINDOW = Duration.ofDays(30);
    /** Anahtar kapalıyken bu sayının altındaki aday kümesi (günlük delta) yine de işlenir. */
    static final int DEFAULT_AUTO_LIMIT = 2000;
    private static final Duration MAX_PAUSE = Duration.ofHours(1);
    private static final Duration PERMANENT_PAUSE = Duration.ofHours(1);
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(CatalogClassificationService.class);
    private static final String PROVIDER_CODE = "STEAM";
    private static final String ENRICHMENT_MARKER = "IGDB_METADATA";

    private final GameProvider provider;
    private final GameRepository games;
    private final GameExternalReferenceRepository references;
    private final GameFieldOverrideRepository overrides;
    private final GameProviderSyncStateRepository states;
    private final CatalogTermRepository termRepository;
    private final GameTypeClassifier types;
    private final MetadataProvenanceService provenance;
    private final ProviderRetryPolicy retries;
    private final Clock clock;

    /** Büyük birikim anahtarı; varsayılan açık (onaylı). Kapalıyken yalnız küçük aday kümesi işlenir. */
    @Value("${game.catalog.classification.backlog-enabled:true}")
    private boolean backlogEnabled;

    private int autoLimit = DEFAULT_AUTO_LIMIT;
    private long before = Long.MAX_VALUE;
    private long after;
    private boolean descendingTurn = true;
    private boolean passActive;
    private boolean passGated;
    private long passClassified;
    private long passEvaluated;
    private long enrichBefore = Long.MAX_VALUE;
    private boolean enrichActive;
    private long enrichUpdated;
    private int consecutiveFailures;
    private Instant pausedUntil;

    /** Yalnız testler için: tarama durumunu sıfırlar. */
    synchronized void restartPassForTests() {
        before = Long.MAX_VALUE;
        after = 0;
        descendingTurn = true;
        passActive = false;
        passGated = false;
        passClassified = 0;
        passEvaluated = 0;
        enrichBefore = Long.MAX_VALUE;
        enrichActive = false;
        enrichUpdated = 0;
        consecutiveFailures = 0;
        pausedUntil = null;
        autoLimit = DEFAULT_AUTO_LIMIT;
        backlogEnabled = true;
    }

    /** Yalnız testler için: büyük birikim anahtarı ve eşiği. */
    synchronized void gateForTests(int limit, boolean backlog) {
        restartPassForTests();
        autoLimit = limit;
        backlogEnabled = backlog;
    }

    synchronized void backlogEnabledForTests(boolean enabled) { backlogEnabled = enabled; }

    /**
     * Bir toplu sınıflandırma adımını işler.
     *
     * @return bu adımda sınıflanan kayıt sayısı; yapılacak iş yoksa (yetenek yok, duraklatılmış, tur bitti veya büyük birikim
     *         anahtarı kapalı) {@code -1}
     */
    @Transactional
    public synchronized int classifyNextBatch() {
        if (!provider.capabilities().contains(GameProvider.Capability.ITEM_TYPE)) return -1;
        Instant now = clock.instant();
        if (pausedUntil != null && now.isBefore(pausedUntil)) return -1;
        // Kimlik eşleme ve katalog aktarımıyla aynı kilit sırası: önce senkron durumu, sonra oyun satırları.
        states.lockProvider(PROVIDER_CODE).orElseThrow();
        Instant recentSince = now.minus(RECENT_WINDOW);
        Instant cutoffRecent = now.minus(RECHECK_RECENT);
        Instant cutoffOld = now.minus(RECHECK_AFTER);
        if (!passActive) {
            long candidates = references.countClassificationCandidates(recentSince, cutoffRecent, cutoffOld);
            passGated = candidates > autoLimit && !backlogEnabled;
            passActive = true;
            before = Long.MAX_VALUE;
            after = 0;
            descendingTurn = true;
            LOG.info("Toplu sınıflandırma turu başladı: aday={} {}", candidates, passGated ? "(büyük birikim anahtarı kapalı: işlenmeyecek)" : "");
        }
        if (passGated) return endPass(now, "büyük birikim anahtarı kapalı");
        var rows = nextRows(recentSince, cutoffRecent, cutoffOld);
        if (rows == null) return endPass(now, "tamamlandı");
        List<String> externalIds = rows.stream().map(row -> row.getExternalId()).toList();
        Map<String, GameProvider.ItemEvidence> evidence;
        try {
            evidence = provider.itemEvidence(externalIds);
        } catch (ProviderException error) {
            pause(error, now);
            return -1;
        }
        consecutiveFailures = 0;
        games.lockCatalogGames(PROVIDER_CODE, externalIds);
        Map<String, Set<OverridableField>> locked = lockedFields(externalIds);
        Map<UUID, Game> loaded = load(rows);
        int classified = 0;
        Map<String, CatalogTerm> termCache = new HashMap<>();
        for (var row : rows) {
            if (classify(loaded.get(row.getGameId()), evidence.get(row.getExternalId()), locked.getOrDefault(row.getExternalId(), Set.of()), now, termCache)) classified++;
        }
        advance(rows);
        passClassified += classified;
        passEvaluated += rows.size();
        return classified;
    }

    /** Dönüşümlü akış: sırayla en yeni uçtan (azalan) ve en eski uçtan (artan) bir grup; biten akış diğerine bırakır. */
    private List<GameExternalReferenceRepository.ClassificationRow> nextRows(Instant recentSince, Instant cutoffRecent, Instant cutoffOld) {
        for (int attempt = 0; attempt < 2; attempt++) {
            var rows = descendingTurn
                    ? references.classificationBatchDesc(before, recentSince, cutoffRecent, cutoffOld, BATCH)
                    : references.classificationBatchAsc(after, recentSince, cutoffRecent, cutoffOld, BATCH).stream()
                            .filter(row -> Long.parseLong(row.getExternalId()) < before).toList();
            if (!rows.isEmpty()) return rows;
            if (descendingTurn) return null;   // azalan akış bitti: aday kalmadı
            descendingTurn = true;             // artan akış bitti: azalan akıştan devam
        }
        return null;
    }

    private void advance(List<GameExternalReferenceRepository.ClassificationRow> rows) {
        if (descendingTurn) before = Long.parseLong(rows.getLast().getExternalId());
        else after = Long.parseLong(rows.getLast().getExternalId());
        descendingTurn = !descendingTurn;
    }

    private int endPass(Instant now, String reason) {
        LOG.info("Toplu sınıflandırma turu bitti ({}): değerlendirilen={} sınıflanan={}", reason, passEvaluated, passClassified);
        passActive = false;
        before = Long.MAX_VALUE;
        after = 0;
        descendingTurn = true;
        passClassified = 0;
        passEvaluated = 0;
        pausedUntil = now.plus(PASS_INTERVAL);
        return -1;
    }

    /**
     * Bir metadata zenginleştirme adımını işler (türü doğrulanmış ama IGDB metadata'sı hiç denenmemiş kayıtlar).
     *
     * @return bu adımda metadata yazılan kayıt sayısı; iş yoksa (yetenek yok, duraklatılmış veya bitti) {@code -1}
     */
    @Transactional
    public synchronized int enrichNextBatch() {
        if (!provider.capabilities().contains(GameProvider.Capability.ITEM_TYPE)) return -1;
        Instant now = clock.instant();
        if (pausedUntil != null && now.isBefore(pausedUntil)) return -1;
        states.lockProvider(PROVIDER_CODE).orElseThrow();
        if (!enrichActive) { enrichActive = true; enrichBefore = Long.MAX_VALUE; }
        var rows = references.enrichmentBatch(enrichBefore, BATCH);
        if (rows.isEmpty()) {
            if (enrichUpdated > 0) LOG.info("Metadata zenginleştirme bitti: yazılan={}", enrichUpdated);
            enrichActive = false;
            enrichUpdated = 0;
            return -1;
        }
        List<String> externalIds = rows.stream().map(row -> row.getExternalId()).toList();
        Map<String, GameProvider.ItemEvidence> evidence;
        try {
            evidence = provider.itemEvidence(externalIds);
        } catch (ProviderException error) {
            pause(error, now);
            return -1;
        }
        consecutiveFailures = 0;
        games.lockCatalogGames(PROVIDER_CODE, externalIds);
        Map<String, Set<OverridableField>> locked = lockedFields(externalIds);
        Map<UUID, Game> loaded = load(rows);
        int updated = 0;
        Map<String, CatalogTerm> termCache = new HashMap<>();
        for (var row : rows) {
            if (enrich(loaded.get(row.getGameId()), evidence.get(row.getExternalId()), locked.getOrDefault(row.getExternalId(), Set.of()), now, termCache)) updated++;
        }
        enrichBefore = Long.parseLong(rows.getLast().getExternalId());
        enrichUpdated += updated;
        return updated;
    }

    private Map<String, Set<OverridableField>> lockedFields(List<String> externalIds) {
        Map<String, Set<OverridableField>> locked = new HashMap<>();
        overrides.lockedFields(PROVIDER_CODE, externalIds).forEach(row ->
                locked.computeIfAbsent(row.getExternalId(), key -> EnumSet.noneOf(OverridableField.class)).add(row.getField()));
        return locked;
    }

    private Map<UUID, Game> load(List<GameExternalReferenceRepository.ClassificationRow> rows) {
        return games.findAllById(rows.stream().map(row -> row.getGameId()).toList()).stream()
                .collect(Collectors.toMap(Game::getId, Function.identity()));
    }

    /**
     * Bir satırı sınıflandırır; sınıflandırılırsa true. Kanıt yoksa/tür bilinmiyorsa kayıt UNKNOWN kalır ve beklemeye alınması için
     * GAME_TYPE/UNKNOWN gözlemi yazılır (manuel kilitli kayıt hariç).
     */
    private boolean classify(Game game, GameProvider.ItemEvidence evidence, Set<OverridableField> locked, Instant now, Map<String, CatalogTerm> termCache) {
        if (game == null || locked.contains(OverridableField.GAME_TYPE) || game.getStatus() != GameStatus.ACTIVE
                || game.getOrigin() != GameOrigin.EXTERNAL || game.getGameType() != GameType.UNKNOWN) return false;
        GameType type = evidence == null ? GameType.UNKNOWN : types.classify(evidence.providerType(), true);
        if (type == GameType.UNKNOWN) {
            provenance.recordObservation(game, "GAME_TYPE", provider.detailSource(), MetadataDataState.UNKNOWN, false);
            return false;
        }
        game.setGameType(type);
        provenance.recordObservation(game, "GAME_TYPE", provider.detailSource(), MetadataDataState.AVAILABLE, false);
        applyMetadata(game, evidence, locked, termCache);
        provenance.recordObservation(game, ENRICHMENT_MARKER, provider.detailSource(), MetadataDataState.AVAILABLE, false);
        game.setUpdatedAt(now);
        return true;
    }

    /** Türü doğrulanmış kayda yalnız boş alanları yazar; tür değişmez. Kanıt yoksa EMPTY gözlemi yazılır (tekrar sorulmaz). */
    private boolean enrich(Game game, GameProvider.ItemEvidence evidence, Set<OverridableField> locked, Instant now, Map<String, CatalogTerm> termCache) {
        if (game == null || game.getStatus() != GameStatus.ACTIVE || game.getOrigin() != GameOrigin.EXTERNAL
                || (game.getGameType() != GameType.MAIN_GAME && game.getGameType() != GameType.DLC)) return false;
        if (evidence == null) {
            provenance.recordObservation(game, ENRICHMENT_MARKER, provider.detailSource(), MetadataDataState.EMPTY, false);
            return false;
        }
        applyMetadata(game, evidence, locked, termCache);
        provenance.recordObservation(game, ENRICHMENT_MARKER, provider.detailSource(), MetadataDataState.AVAILABLE, false);
        game.setUpdatedAt(now);
        return true;
    }

    /** Çıkış tarihi (yalnız boşsa ve kilit yoksa) ve tür/platform/kategori/etiket terimleri (yalnız o türde terim yoksa). */
    private void applyMetadata(Game game, GameProvider.ItemEvidence evidence, Set<OverridableField> locked, Map<String, CatalogTerm> termCache) {
        if (evidence.releaseDate() != null && game.getReleaseDate() == null && !locked.contains(OverridableField.RELEASE_DATE)) {
            game.setReleaseDate(evidence.releaseDate());
            provenance.recordObservation(game, "RELEASE_DATE", provider.detailSource(), MetadataDataState.AVAILABLE, false);
        }
        attachTerms(game, TermKind.GENRE, "GENRES", evidence.genres(), termCache);
        attachTerms(game, TermKind.PLATFORM, "PLATFORMS", evidence.platforms(), termCache);
        attachTerms(game, TermKind.CATEGORY, "CATEGORIES", evidence.categories(), termCache);
        attachTerms(game, TermKind.TAG, "TAGS", evidence.tags(), termCache);
    }

    /**
     * Sağlayıcı kodlarını yalnız o türde hiç terimi olmayan oyuna yazar: yönetici verisi ve önceki aktarım ezilmez, eksik veya
     * geçersiz kod atlanır (uydurulmaz). Terim satırı yoksa kod ve (varsa) İngilizce adla oluşturulur.
     */
    private void attachTerms(Game game, TermKind kind, String field, List<GameProvider.TermEvidence> evidence, Map<String, CatalogTerm> termCache) {
        if (evidence.isEmpty() || game.getTerms().stream().anyMatch(term -> term.getKind() == kind)) return;
        for (GameProvider.TermEvidence item : evidence) {
            CatalogTerm term = termCache.computeIfAbsent(kind + ":" + item.code(), key -> termRepository.findByKindAndCode(kind, item.code()).orElseGet(() -> {
                CatalogTerm created = new CatalogTerm();
                created.setKind(kind);
                created.setCode(item.code());
                created.setNameEn(item.name());
                return termRepository.save(created);
            }));
            game.getTerms().add(term);
        }
        provenance.recordObservation(game, field, provider.detailSource(), MetadataDataState.AVAILABLE, false);
    }

    /** Hata sonrası duraklama: geçici hatada artan geri çekilme (sınır {@link #MAX_PAUSE}), kalıcı hatada uzun bekleme. */
    private void pause(ProviderException error, Instant now) {
        consecutiveFailures = Math.min(consecutiveFailures + 1, 6);
        Duration delay = retries.delay(error, Math.min(consecutiveFailures, 5));
        Duration pause = delay == null ? PERMANENT_PAUSE : delay.multipliedBy(consecutiveFailures >= 2 ? 4L : 1L);
        if (pause.compareTo(Duration.ofMinutes(1)) < 0) pause = Duration.ofMinutes(1);
        if (pause.compareTo(MAX_PAUSE) > 0) pause = MAX_PAUSE;
        pausedUntil = now.plus(pause);
        LOG.warn("Toplu IGDB işlemi duraklatıldı: kod={} ardışıkHata={} devam={}", error.code(), consecutiveFailures, pausedUntil);
    }
}

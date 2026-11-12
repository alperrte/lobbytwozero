package com.ltz.game.application.service;

import com.ltz.game.api.dto.response.CatalogStatusResponse;
import com.ltz.game.application.port.GameProvider;
import com.ltz.game.domain.entity.Game;
import com.ltz.game.domain.entity.GameExternalReference;
import com.ltz.game.domain.entity.GameProviderSyncState;
import com.ltz.game.domain.enums.CatalogRunMode;
import com.ltz.game.domain.enums.CatalogSyncStatus;
import com.ltz.game.domain.enums.GameOrigin;
import com.ltz.game.domain.enums.GameType;
import com.ltz.game.domain.enums.MetadataDataState;
import com.ltz.game.domain.enums.OverridableField;
import com.ltz.game.domain.exception.ProviderException;
import com.ltz.game.infrastructure.persistence.repository.GameExternalReferenceRepository;
import com.ltz.game.infrastructure.persistence.repository.GameFieldOverrideRepository;
import com.ltz.game.infrastructure.persistence.repository.GameProviderSyncStateRepository;
import com.ltz.game.infrastructure.persistence.repository.GameRepository;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Her çağrıda tek sayfa; checkpoint ve retry durumu aynı transaction'da saklanır. */
@Service @RequiredArgsConstructor
public class CatalogImportService {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(CatalogImportService.class);
    private static final String PROVIDER_CODE = "STEAM";
    /** Yeni oyun ve DLC'lerin Steam'den ne kadar sıklıkla alınacağı (varsayılan geliştirme: 1 saat; canlı profil 15 dakika). */
    static final Duration DEFAULT_SYNC_INTERVAL = Duration.ofHours(1);
    private final GameProvider provider;
    private final GameTypeClassifier types;
    private final GameProviderSyncStateRepository states;
    private final GameRepository games;
    private final GameExternalReferenceRepository references;
    private final Clock clock;
    private final ProviderRetryPolicy retries;
    private final GameFieldOverrideRepository overrides;
    private final MetadataProvenanceService provenance;
    /** Artımlı tur aralığı: son başarılı turdan bu kadar sonra yeni tur başlar; tur yalnız değişen kayıtları ister, kararlı durumda ucuzdur. */
    private Duration syncInterval = DEFAULT_SYNC_INTERVAL;

    /** ISO-8601 süre ({@code PT15M}); metin olarak alınır ki Spring dönüştürme hizmeti olmayan bağlamlarda da çalışsın. Geçersiz değer açılışta hata verir. */
    @org.springframework.beans.factory.annotation.Value("${ltz.game.catalog.sync-interval:PT1H}")
    void configureSyncInterval(String text) {
        Duration parsed = Duration.parse(text.strip());
        if (parsed.compareTo(Duration.ofMinutes(1)) < 0 || parsed.compareTo(Duration.ofDays(7)) > 0) throw new IllegalArgumentException("Geçersiz katalog tur aralığı.");
        this.syncInterval = parsed;
    }

    /** Yalnız testler için: tur aralığı. */
    void syncIntervalForTests(Duration interval) { this.syncInterval = interval; }

    @Transactional
    public CatalogStatusResponse startInitial() { return start(CatalogRunMode.INITIAL); }

    @Transactional
    public CatalogStatusResponse startIncremental() { return start(CatalogRunMode.INCREMENTAL); }

    private CatalogStatusResponse start(CatalogRunMode mode) {
        var state = lockedState();
        if (state.getSyncStatus() == CatalogSyncStatus.RUNNING) throw new IllegalStateException("Aktarım zaten çalışıyor.");
        if (mode == CatalogRunMode.INCREMENTAL && state.getLastSuccessfulAt() == null)
            throw new IllegalStateException("Önce ilk aktarım tamamlanmalıdır.");
        state.setRunMode(mode);
        // Aynı saniyedeki güncellemeleri kaçırmamak için bir saniyelik örtüşme.
        state.setQuerySince(mode == CatalogRunMode.INCREMENTAL ? state.getLastSuccessfulAt().minusSeconds(1) : null);
        state.setCursor(0);
        state.setProcessedCount(0);
        state.setRunStartedAt(clock.instant());
        state.setLastErrorCode(null);
        state.setFailureCount(0);
        state.setNextAttemptAt(null);
        state.setSyncStatus(CatalogSyncStatus.RUNNING);
        return response(state);
    }

    /** Yeniden başlatmadan veya geçici hatadan sonra cursor sıfırlanmadan devam eder. */
    @Transactional
    public CatalogStatusResponse resumeFailed() {
        var state = lockedState();
        if (state.getSyncStatus() != CatalogSyncStatus.FAILED) throw new IllegalStateException("Başarısız aktarım yok.");
        state.setSyncStatus(CatalogSyncStatus.RUNNING);
        state.setFailureCount(0);
        state.setNextAttemptAt(null);
        state.setLastErrorCode(null);
        return response(state);
    }

    /**
     * Süreç (yeniden) başlarken çağrılır: otomatik tekrar hakkı biten veya kalıcı sayılan hata nedeniyle FAILED kalmış
     * aktarımı cursor'ı koruyarak sürdürür. Dağıtım/yeniden başlatma operatör müdahalesidir; süreç başına en çok bir kez.
     */
    @Transactional
    public boolean resumeAfterRestart() {
        var state = lockedState();
        if (state.getSyncStatus() != CatalogSyncStatus.FAILED) return false;
        LOG.info("Başarısız katalog aktarımı yeniden başlatmada sürdürülüyor: cursor={} sonSonuç={}", state.getCursor(), state.getLastErrorCode());
        resumeFailed();
        return true;
    }

    @Transactional
    public CatalogStatusResponse synchronizeNextPage() {
        var state = lockedState();
        Instant now = clock.instant();
        if (state.getSyncStatus() == CatalogSyncStatus.FAILED) {
            if (state.getNextAttemptAt() == null) {
                // Otomatik tekrar bütçesi bitti. Artımlı tur bir sonraki aralıkta, önceki başarılı checkpoint'ten yeniden
                // başlar (kaçırılan güncellemeler alınır); ilk aktarım yalnız açık devam (yeniden başlatma) ile sürer.
                if (!incrementalRetryDue(state, now)) return response(state);
                LOG.warn("Başarısız artımlı tur yeni aralıkta yeniden başlatılıyor: önceki kod={}", state.getLastErrorCode());
                start(CatalogRunMode.INCREMENTAL);
            } else {
                if (now.isBefore(state.getNextAttemptAt())) return response(state);
                state.setSyncStatus(CatalogSyncStatus.RUNNING);
            }
        } else if (state.getSyncStatus() == CatalogSyncStatus.IDLE) {
            start(CatalogRunMode.INITIAL);
        } else if (state.getSyncStatus() == CatalogSyncStatus.COMPLETE) {
            // Her aralıkta bir kez: son başarılı turdan bu yana yapılandırılmış süre geçtiyse yeni artımlı tur başlar.
            if (state.getLastSuccessfulAt().plus(syncInterval).isAfter(now)) return response(state);
            start(CatalogRunMode.INCREMENTAL);
        }
        return importPage(1000);
    }


    private boolean incrementalRetryDue(GameProviderSyncState state, Instant now) {
        return state.getRunMode() == CatalogRunMode.INCREMENTAL && state.getLastSuccessfulAt() != null && state.getRunStartedAt() != null
                && !state.getRunStartedAt().plus(syncInterval).isAfter(now);
    }

    /** Bir HTTP sayfası ve yerel checkpoint aynı DB transaction'ında işlenir. */
    @Transactional
    public CatalogStatusResponse importNextPage(int size) {
        return importPage(size);
    }

    private CatalogStatusResponse importPage(int size) {
        var state = lockedState();
        if (state.getSyncStatus() != CatalogSyncStatus.RUNNING) return response(state);
        if (state.getNextAttemptAt() != null && clock.instant().isBefore(state.getNextAttemptAt())) return response(state);
        GameProvider.CatalogPage page;
        try {
            page = provider.catalog(new GameProvider.CatalogQuery(state.getCursor(), state.getQuerySince(), size));
        } catch (ProviderException exception) {
            state.setLastErrorCode(exception.code());
            state.setSyncStatus(CatalogSyncStatus.FAILED);
            state.setFailureCount(Math.min(6, state.getFailureCount() + 1));
            Duration delay = retries.delay(exception, state.getFailureCount());
            state.setNextAttemptAt(delay == null ? null : clock.instant().plus(delay));
            // Sağlayıcı hatası sessiz kalmaz: durum yanıtındaki lastErrorCode ile aynı kod ve cursor loglanır.
            LOG.error("Steam katalog aktarımı başarısız: kod={} cursor={} ardışıkHata={} sonrakiDeneme={}",
                    exception.code(), state.getCursor(), state.getFailureCount(), state.getNextAttemptAt());
            return response(state);
        }
        Map<String, Set<OverridableField>> locked = lockedFields(page.items());
        for (var item : page.items()) importItem(item, locked.getOrDefault(item.externalId(), Set.of()));
        if (page.skippedRecords() > 0)
            LOG.warn("Steam sayfası tamamlandı: işlenen={} atlanan={} cursor={}", page.items().size(), page.skippedRecords(), page.nextAppId());
        state.setCursor(page.nextAppId());
        state.setProcessedCount(state.getProcessedCount() + page.items().size());
        state.setFailureCount(0);
        state.setLastErrorCode(null);
        state.setNextAttemptAt(null);
        if (!page.hasMore()) {
            state.setSyncStatus(CatalogSyncStatus.COMPLETE);
            // Çalışma başlangıcı, sayfalar okunurken yapılan değişiklikleri sonraki turda yeniden kapsar.
            state.setLastSuccessfulAt(state.getRunStartedAt());
        }
        references.flush();
        return response(state);
    }

    /** Admin tarafından manuel düzeltilmiş alanlar (GAME-07); sayfa için tek sorguyla okunur. */
    private Map<String, Set<OverridableField>> lockedFields(List<GameProvider.CatalogItem> items) {
        if (items.isEmpty()) return Map.of();
        var externalIds = items.stream().map(GameProvider.CatalogItem::externalId).toList();
        // Alan kilitleri okunmadan önce admin ile aynı oyun satırlarını kilitle.
        // Böylece eski override bilgisi, daha yeni entity sürümüyle birlikte yazılamaz.
        games.lockCatalogGames(PROVIDER_CODE, externalIds);
        Map<String, Set<OverridableField>> locked = new HashMap<>();
        overrides.lockedFields(PROVIDER_CODE, externalIds)
                .forEach(row -> locked.computeIfAbsent(row.getExternalId(), key -> EnumSet.noneOf(OverridableField.class)).add(row.getField()));
        return locked;
    }

    /**
     * Steam'in {@code last_modified} değeri, kaydın son doğrulanan senkron zamanından (lastSyncedAt) sonra değilse kayıt
     * değişmemiştir ve hiçbir yazma yapılmaz. Yeni kayıt oluşturulur; değişen kayıtta yalnız farklı başlık güncellenir
     * (manuel başlık kilidi ve INTERNAL başlık korunur), IGDB'nin "kanıt yok" gözlemi silinir ve kayıt yeniden sınıflandırmaya uygun olur.
     */
    private void importItem(GameProvider.CatalogItem item, Set<OverridableField> locked) {
        var reference = references.findByProviderAndExternalId(PROVIDER_CODE, item.externalId()).orElse(null);
        Instant now = clock.instant();
        if (reference == null) {
            Game game = new Game();
            game.setOrigin(GameOrigin.EXTERNAL);
            game.setTitle(item.title());
            game.setNormalizedTitle(normalize(item.title()));
            provenance.recordObservation(game, "TITLE", PROVIDER_CODE, MetadataDataState.AVAILABLE, false);
            game = games.save(game);
            reference = new GameExternalReference();
            reference.setGame(game);
            reference.setProvider(PROVIDER_CODE);
            reference.setExternalId(item.externalId());
            applyObservedType(game, item, locked);
            reference.setLastSyncedAt(now);
            references.save(reference);
            return;
        }
        Instant synced = reference.getLastSyncedAt();
        if (synced != null && item.lastModified() != null && !item.lastModified().isAfter(synced)) return;
        Game game = reference.getGame();
        // Eşlenmiş INTERNAL kayıtların birincil LTZ başlığı sağlayıcı tarafından ezilmez.
        if (game.getOrigin() == GameOrigin.EXTERNAL && !locked.contains(OverridableField.TITLE) && !item.title().equals(game.getTitle())) {
            game.setTitle(item.title());
            game.setNormalizedTitle(normalize(item.title()));
            game.setUpdatedAt(now);
            provenance.recordObservation(game, "TITLE", PROVIDER_CODE, MetadataDataState.AVAILABLE, false);
        }
        applyObservedType(game, item, locked);
        var observed = game.getMetadataProvenance().get("GAME_TYPE");
        if (game.getGameType() == GameType.UNKNOWN && observed != null && observed.getDataState() == MetadataDataState.UNKNOWN) {
            game.getMetadataProvenance().remove("GAME_TYPE");
        }
        reference.setLastSyncedAt(now);
        references.save(reference);
    }

    private void applyObservedType(Game game, GameProvider.CatalogItem item, Set<OverridableField> locked) {
        if (game.getOrigin() == GameOrigin.EXTERNAL && !locked.contains(OverridableField.GAME_TYPE)
                && provider.capabilities().contains(GameProvider.Capability.ITEM_TYPE)) {
            GameType observed = types.classify(item.providerType(), true);
            if (observed != GameType.UNKNOWN) {
                game.setGameType(observed);
                provenance.recordObservation(game, "GAME_TYPE", PROVIDER_CODE, MetadataDataState.AVAILABLE, false);
            }
        }
    }

    @Transactional(readOnly=true)
    public CatalogStatusResponse status() { return response(states.findByProvider(PROVIDER_CODE).orElseThrow()); }

    private GameProviderSyncState lockedState() { return states.lockProvider(PROVIDER_CODE).orElseThrow(); }
    private CatalogStatusResponse response(GameProviderSyncState state) {
        return new CatalogStatusResponse(PROVIDER_CODE, state.getRunMode(), state.getSyncStatus(), state.getCursor(),
                state.getProcessedCount(), references.countByProvider(PROVIDER_CODE), references.countByProviderAndGameGameType(PROVIDER_CODE, GameType.MAIN_GAME),
                references.countByProviderAndGameGameType(PROVIDER_CODE, GameType.UNKNOWN), null, state.getRunStartedAt(),
                state.getLastSuccessfulAt(), state.getLastErrorCode(), state.getFailureCount(), state.getNextAttemptAt());
    }
    public static String normalize(String title) {
        return Normalizer.normalize(title, Normalizer.Form.NFKC).strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}

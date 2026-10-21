package com.ltz.game.application.service;

import com.ltz.game.api.dto.response.CatalogStatusResponse;
import com.ltz.game.application.port.GameProvider;
import com.ltz.game.domain.entity.*;
import com.ltz.game.domain.enums.*;
import com.ltz.game.domain.exception.ProviderException;
import com.ltz.game.infrastructure.persistence.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.text.Normalizer;
import java.time.Instant;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Her çağrıda tek sayfa; checkpoint ve retry durumu aynı transaction'da saklanır. */
@Service @RequiredArgsConstructor
public class CatalogImportService {
    private static final String PROVIDER_CODE = "STEAM";
    private final GameProvider provider;
    private final GameTypeClassifier types;
    private final GameProviderSyncStateRepository states;
    private final GameRepository games;
    private final GameExternalReferenceRepository references;
    private final Clock clock;
    private final ProviderRetryPolicy retries;
    private final GameFieldOverrideRepository overrides;

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

    @Transactional
    public CatalogStatusResponse synchronizeNextPage() {
        var state = lockedState();
        Instant now = clock.instant();
        if (state.getSyncStatus() == CatalogSyncStatus.FAILED) {
            if (state.getNextAttemptAt() == null || now.isBefore(state.getNextAttemptAt())) return response(state);
            state.setSyncStatus(CatalogSyncStatus.RUNNING);
        } else if (state.getSyncStatus() == CatalogSyncStatus.IDLE) {
            start(CatalogRunMode.INITIAL);
        } else if (state.getSyncStatus() == CatalogSyncStatus.COMPLETE) {
            if (now.isBefore(state.getLastSuccessfulAt().plus(Duration.ofHours(6)))) return response(state);
            start(CatalogRunMode.INCREMENTAL);
        }
        return importPage(1000);
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
            return response(state);
        }
        Map<String, Set<OverridableField>> locked = lockedFields(page.items());
        for (var item : page.items()) importItem(item, locked.getOrDefault(item.externalId(), Set.of()));
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
        Map<String, Set<OverridableField>> locked = new HashMap<>();
        overrides.lockedFields(PROVIDER_CODE, items.stream().map(GameProvider.CatalogItem::externalId).toList())
                .forEach(row -> locked.computeIfAbsent(row.getExternalId(), key -> EnumSet.noneOf(OverridableField.class)).add(row.getField()));
        return locked;
    }

    private void importItem(GameProvider.CatalogItem item, Set<OverridableField> locked) {
            var reference = references.findByProviderAndExternalId(PROVIDER_CODE, item.externalId()).orElse(null);
            Game game;
            if (reference == null) {
                game = new Game();
                game.setOrigin(GameOrigin.EXTERNAL);
                game.setTitle(item.title());
                game.setNormalizedTitle(normalize(item.title()));
                game = games.save(game);
                reference = new GameExternalReference();
                reference.setGame(game);
                reference.setProvider(PROVIDER_CODE);
                reference.setExternalId(item.externalId());
            } else {
                game = reference.getGame();
                // Eşlenmiş INTERNAL kayıtların birincil LTZ başlığı sağlayıcı tarafından ezilmez.
                if (game.getOrigin() == GameOrigin.EXTERNAL && !locked.contains(OverridableField.TITLE)) {
                    game.setTitle(item.title());
                    game.setNormalizedTitle(normalize(item.title()));
                    game.setUpdatedAt(Instant.now());
                }
            }
            if (game.getOrigin() == GameOrigin.EXTERNAL && !locked.contains(OverridableField.GAME_TYPE)
                    && provider.capabilities().contains(GameProvider.Capability.ITEM_TYPE)) {
                GameType observed=types.classify(item.providerType(), true);
                if (observed != GameType.UNKNOWN) game.setGameType(observed);
            }
            reference.setLastSyncedAt(Instant.now());
            references.save(reference);
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

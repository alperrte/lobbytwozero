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
import java.util.Locale;

/** Elle çağrılan sayfalı aktarım. Zamanlayıcı, otomatik retry ve detay yenileme Faz 2 kapsamıdır. */
@Service @RequiredArgsConstructor
public class CatalogImportService {
    private static final String PROVIDER = "STEAM";
    private final GameProvider provider;
    private final GameTypeClassifier types;
    private final GameProviderSyncStateRepository states;
    private final GameRepository games;
    private final GameExternalReferenceRepository references;

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
        state.setRunStartedAt(Instant.now());
        state.setLastErrorCode(null);
        state.setSyncStatus(CatalogSyncStatus.RUNNING);
        return response(state);
    }

    /** Bir HTTP sayfası ve yerel checkpoint aynı DB transaction'ında işlenir. */
    @Transactional
    public CatalogStatusResponse importNextPage(int size) {
        var state = lockedState();
        if (state.getSyncStatus() != CatalogSyncStatus.RUNNING) return response(state);
        GameProvider.CatalogPage page;
        try {
            page = provider.catalog(new GameProvider.CatalogQuery(state.getCursor(), state.getQuerySince(), size));
        } catch (ProviderException exception) {
            state.setLastErrorCode(exception.code());
            state.setSyncStatus(CatalogSyncStatus.FAILED);
            return response(state);
        }
        for (var item : page.items()) {
            var reference = references.findByProviderAndExternalId(PROVIDER, item.externalId()).orElse(null);
            Game game;
            if (reference == null) {
                game = new Game();
                game.setOrigin(GameOrigin.EXTERNAL);
                game.setTitle(item.title());
                game.setNormalizedTitle(normalize(item.title()));
                game = games.save(game);
                reference = new GameExternalReference();
                reference.setGame(game);
                reference.setProvider(PROVIDER);
                reference.setExternalId(item.externalId());
            } else {
                game = reference.getGame();
                // Eşlenmiş INTERNAL kayıtların birincil LTZ başlığı sağlayıcı tarafından ezilmez.
                if (game.getOrigin() == GameOrigin.EXTERNAL) {
                    game.setTitle(item.title());
                    game.setNormalizedTitle(normalize(item.title()));
                    game.setUpdatedAt(Instant.now());
                }
            }
            if (game.getOrigin() == GameOrigin.EXTERNAL && provider.capabilities().contains(GameProvider.Capability.ITEM_TYPE)) {
                GameType observed=types.classify(item.providerType(), true);
                if (observed != GameType.UNKNOWN) game.setGameType(observed);
            }
            reference.setLastSyncedAt(Instant.now());
            references.save(reference);
        }
        state.setCursor(page.nextAppId());
        state.setProcessedCount(state.getProcessedCount() + page.items().size());
        if (!page.hasMore()) {
            state.setSyncStatus(CatalogSyncStatus.COMPLETE);
            // Çalışma başlangıcı, sayfalar okunurken yapılan değişiklikleri sonraki turda yeniden kapsar.
            state.setLastSuccessfulAt(state.getRunStartedAt());
        }
        references.flush();
        return response(state);
    }

    @Transactional(readOnly=true)
    public CatalogStatusResponse status() { return response(states.findByProvider(PROVIDER).orElseThrow()); }

    private GameProviderSyncState lockedState() { return states.lockProvider(PROVIDER).orElseThrow(); }
    private CatalogStatusResponse response(GameProviderSyncState state) {
        return new CatalogStatusResponse(PROVIDER, state.getRunMode(), state.getSyncStatus(), state.getCursor(),
                state.getProcessedCount(), references.countByProvider(PROVIDER), references.countByProviderAndGameGameType(PROVIDER, GameType.MAIN_GAME),
                references.countByProviderAndGameGameType(PROVIDER, GameType.UNKNOWN), null, state.getRunStartedAt(),
                state.getLastSuccessfulAt(), state.getLastErrorCode());
    }
    public static String normalize(String title) {
        return Normalizer.normalize(title, Normalizer.Form.NFKC).strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}

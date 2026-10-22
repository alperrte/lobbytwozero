package com.ltz.game.application.service;

import com.ltz.game.application.port.GameProvider;
import com.ltz.game.domain.entity.Game;
import com.ltz.game.domain.enums.*;
import com.ltz.game.domain.exception.*;
import com.ltz.game.infrastructure.persistence.repository.*;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** İç uygulama komutu. Public GET yazma yapmaz; gerçek detay adaptörü henüz yoktur. */
@Service @RequiredArgsConstructor
public class GameDetailRefreshService {
    private final GameProvider provider;
    private final GameRepository games;
    private final GameProviderSyncStateRepository states;
    private final GameTypeClassifier types;
    private final ProviderRetryPolicy retries;
    private final GameFieldOverrideRepository overrides;
    private final Clock clock;
    private final MetadataProvenanceService provenance;

    @Transactional(readOnly = true)
    public java.util.List<UUID> dueCandidates() {
        if (!provider.capabilities().contains(GameProvider.Capability.DETAILS)) return java.util.List.of();
        return games.detailRefreshCandidates(clock.instant(), org.springframework.data.domain.PageRequest.of(0, 1));
    }

    @Transactional
    public String refresh(UUID gameId) {
        // Kimlik eşleme ve katalog aktarımıyla aynı kilit sırası.
        states.lockProvider(provider.provider()).orElseThrow();
        Game game = games.lockGame(gameId).filter(Game::isActive).orElseThrow(GameNotFoundException::new);
        if (game.getOrigin() == GameOrigin.INTERNAL) return "LOCAL";
        if (!provider.capabilities().contains(GameProvider.Capability.DETAILS)) return "UNSUPPORTED";
        if (game.getDetailsExpiresAt() != null && clock.instant().isBefore(game.getDetailsExpiresAt())) return "FRESH";
        if (game.getDetailsNextAttemptAt() != null && clock.instant().isBefore(game.getDetailsNextAttemptAt())) return "BACKOFF";
        if (game.getDetailsLastErrorCode() != null && game.getDetailsNextAttemptAt() == null) return "STOPPED";
        var reference = game.getExternalReferences().stream().filter(ref -> ref.getProvider().equals(provider.provider()))
                .sorted(java.util.Comparator.comparing(com.ltz.game.domain.entity.GameExternalReference::getExternalId))
                .findFirst().orElseThrow(GameNotFoundException::new);
        try {
            GameProvider.Details details = provider.details(reference.getExternalId());
            if (details == null) throw new ProviderException("INVALID_PROVIDER_RESPONSE");
            applyDetails(game, details, lockedFields(game));
            game.setDetailsFetchedAt(clock.instant());
            game.setDetailsExpiresAt(clock.instant().plus(Duration.ofHours(24)));
            game.setDetailsFailureCount(0);
            game.setDetailsNextAttemptAt(null);
            game.setDetailsLastErrorCode(null);
            // Sınırlı alan kümesi hiçbir zaman COMPLETE metadata iddiası üretmez.
            if (game.getMetadataCompleteness() == MetadataCompleteness.INDEX_ONLY)
                game.setMetadataCompleteness(MetadataCompleteness.PARTIAL);
            game.setUpdatedAt(clock.instant());
            return "REFRESHED";
        } catch (ProviderException error) {
            game.setDetailsFailureCount(Math.min(6, game.getDetailsFailureCount() + 1));
            game.setDetailsLastErrorCode(error.code());
            Duration delay = retries.delay(error, game.getDetailsFailureCount());
            game.setDetailsNextAttemptAt(delay == null ? null : clock.instant().plus(delay));
            return "FAILED";
        }
    }

    /** Admin tarafından manuel düzeltilmiş alanlar (GAME-07) sağlayıcı verisiyle ezilmez. */
    private java.util.Set<OverridableField> lockedFields(Game game) {
        var locked = java.util.EnumSet.noneOf(OverridableField.class);
        overrides.findByGameIdOrderByFieldAsc(game.getId()).forEach(override -> locked.add(override.getField()));
        return locked;
    }

    private void applyDetails(Game game, GameProvider.Details details, java.util.Set<OverridableField> locked) {
        if (details.description() != null && !locked.contains(OverridableField.DESCRIPTION)) {
            game.setDescription(details.description()); observed(game,"DESCRIPTION");
        }
        if (details.releaseDate() != null && !locked.contains(OverridableField.RELEASE_DATE)) {
            game.setReleaseDate(details.releaseDate()); observed(game,"RELEASE_DATE");
        }
        applyCompanies(game,details,locked);
        if (!locked.contains(OverridableField.GAME_TYPE) && provider.capabilities().contains(GameProvider.Capability.ITEM_TYPE)) {
            GameType type = types.classify(details.providerType(), true);
            if (type != GameType.UNKNOWN) { game.setGameType(type); observed(game,"GAME_TYPE"); }
        }
    }
    private void applyCompanies(Game game, GameProvider.Details details, java.util.Set<OverridableField> locked) {
        if (!details.developers().isEmpty() && !locked.contains(OverridableField.DEVELOPERS)) {
            game.getDevelopers().clear(); game.getDevelopers().addAll(details.developers()); observed(game,"DEVELOPERS");
        }
        if (!details.publishers().isEmpty() && !locked.contains(OverridableField.PUBLISHERS)) {
            game.getPublishers().clear(); game.getPublishers().addAll(details.publishers()); observed(game,"PUBLISHERS");
        }
    }
    private void observed(Game game, String field) { provenance.recordObservation(game,field,provider.provider(),MetadataDataState.AVAILABLE,false); }

    /** Kalıcı hatada yalnız açık uygulama komutuyla yeniden denemeye izin verilir. */
    @Transactional
    public void resetFailure(UUID gameId) {
        states.lockProvider(provider.provider()).orElseThrow();
        var game = games.lockGame(gameId).filter(Game::isActive).orElseThrow(GameNotFoundException::new);
        game.setDetailsFailureCount(0);
        game.setDetailsLastErrorCode(null);
        game.setDetailsNextAttemptAt(null);
    }
}

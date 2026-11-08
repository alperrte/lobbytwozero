package com.ltz.game.application.service;

import com.ltz.game.application.port.GameProvider;
import com.ltz.game.domain.entity.Game;
import com.ltz.game.domain.enums.GameOrigin;
import com.ltz.game.domain.enums.GameType;
import com.ltz.game.domain.enums.MetadataCompleteness;
import com.ltz.game.domain.enums.MetadataDataState;
import com.ltz.game.domain.enums.OverridableField;
import com.ltz.game.domain.exception.GameNotFoundException;
import com.ltz.game.domain.exception.ProviderException;
import com.ltz.game.infrastructure.persistence.repository.GameFieldOverrideRepository;
import com.ltz.game.infrastructure.persistence.repository.GameProviderSyncStateRepository;
import com.ltz.game.infrastructure.persistence.repository.GameRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** İç uygulama komutu. Public GET yazma yapmaz; yapılandırılmış detay sağlayıcısı arka planda yeniler. */
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

    /**
     * Detay bootstrap: toplu sınıflandırmanın ana oyun olarak işaretlediği ve detayı hiç denenmemiş Steam kayıtları
     * (en yeni önce). Açıklama, çıkış tarihi, şirket ve dil verisi bu yoldan sağlayıcıdan alınır.
     */
    @Transactional(readOnly = true)
    public java.util.List<UUID> detailBootstrapCandidates(int limit) {
        var capabilities = provider.capabilities();
        if (limit < 1 || !capabilities.contains(GameProvider.Capability.DETAILS)) return java.util.List.of();
        return games.detailBootstrapCandidates(org.springframework.data.domain.PageRequest.of(0, limit));
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
            applyFetched(game, details);
            return "REFRESHED";
        } catch (ProviderException error) {
            game.setDetailsFailureCount(Math.min(6, game.getDetailsFailureCount() + 1));
            game.setDetailsLastErrorCode(error.code());
            Duration delay = retries.delay(error, game.getDetailsFailureCount());
            game.setDetailsNextAttemptAt(delay == null ? null : clock.instant().plus(delay));
            return "FAILED";
        }
    }

    /** Sağlayıcıdan alınmış detayı uygular (tek kayıt ve toplu yol ortak): kilitli alanlar ezilmez, tazelik ve hata durumu sıfırlanır. */
    void applyFetched(Game game, GameProvider.Details details) {
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
    }

    /** Toplu sorguda sağlayıcı bu oyun için kanıt vermedi: tek kayıt yolundaki "bulunamadı" hatasıyla aynı durum (yeniden deneme politikası geçerli). */
    void markNotFound(Game game) {
        var error = new ProviderException("PROVIDER_ITEM_NOT_FOUND");
        game.setDetailsFailureCount(Math.min(6, game.getDetailsFailureCount() + 1));
        game.setDetailsLastErrorCode(error.code());
        Duration delay = retries.delay(error, game.getDetailsFailureCount());
        game.setDetailsNextAttemptAt(delay == null ? null : clock.instant().plus(delay));
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
        applyLanguages(game,details,locked);
        if (GameCoverWriter.apply(game, details.coverUrl(), provider.detailSource())) observed(game,"COVER_URL");
        if (!locked.contains(OverridableField.GAME_TYPE) && provider.capabilities().contains(GameProvider.Capability.ITEM_TYPE)) {
            GameType type = types.classify(details.providerType(), true);
            if (type != GameType.UNKNOWN) { game.setGameType(type); observed(game,"GAME_TYPE"); }
        }
    }
    /**
     * GAME-23: sağlayıcının bildirdiği dil desteği. Yalnız veri varsa yazılır; boş yanıt mevcut dil verisini silmez
     * ve "dil yok" anlamına gelmez. Manuel LOCALIZATION kilidi sağlayıcı verisiyle ezilmez. Sağlayıcının listelemediği
     * ses/altyazı/arayüz türü UNKNOWN kalır. Düz dil listesi yalnız boşken doldurulur (yönetici verisi ezilmez).
     */
    private void applyLanguages(Game game, GameProvider.Details details, java.util.Set<OverridableField> locked) {
        if (details.languages().isEmpty() || locked.contains(OverridableField.LOCALIZATION)) return;
        java.util.Set<com.ltz.game.domain.entity.GameLanguageSupport> supports = new java.util.HashSet<>();
        java.util.Set<String> supported = new java.util.TreeSet<>();
        for (GameProvider.LanguageSupport language : details.languages()) {
            var value = new com.ltz.game.domain.entity.GameLanguageSupport();
            value.setLanguageCode(language.languageCode());
            value.setInterfaceSupport(language.interfaceSupport());
            value.setAudioSupport(language.audioSupport());
            value.setSubtitleSupport(language.subtitleSupport());
            supports.add(value);
            supported.add(language.languageCode());
        }
        game.getLanguageSupport().clear(); game.getLanguageSupport().addAll(supports);
        game.setLanguageDataState(MetadataDataState.AVAILABLE);
        provenance.recordObservation(game,"LOCALIZATION",provider.detailSource(),MetadataDataState.AVAILABLE,false);
        if (game.getLanguages().isEmpty()) { game.getLanguages().addAll(supported); observed(game,"LANGUAGES"); }
    }
    private void applyCompanies(Game game, GameProvider.Details details, java.util.Set<OverridableField> locked) {
        if (!details.developers().isEmpty() && !locked.contains(OverridableField.DEVELOPERS)) {
            game.getDevelopers().clear(); game.getDevelopers().addAll(details.developers()); observed(game,"DEVELOPERS");
        }
        if (!details.publishers().isEmpty() && !locked.contains(OverridableField.PUBLISHERS)) {
            game.getPublishers().clear(); game.getPublishers().addAll(details.publishers()); observed(game,"PUBLISHERS");
        }
    }
    private void observed(Game game, String field) { provenance.recordObservation(game,field,provider.detailSource(),MetadataDataState.AVAILABLE,false); }

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

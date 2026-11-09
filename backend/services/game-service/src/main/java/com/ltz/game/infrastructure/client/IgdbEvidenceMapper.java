package com.ltz.game.infrastructure.client;

import com.ltz.game.application.port.GameProvider;
import com.ltz.game.domain.exception.ProviderException;
import com.ltz.game.infrastructure.client.dto.response.IgdbResponses;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** IGDB oyun kaydını Game'in kanıt modeline çeviren durumsuz yardımcılar. Sağlayıcının vermediği alan uydurulmaz. */
final class IgdbEvidenceMapper {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(IgdbEvidenceMapper.class);

    private IgdbEvidenceMapper() { }

    /** Oyunun tür/tarih/terim kanıtı; hiçbiri yoksa boş. Oyun modları → kategori, temalar → etiket (IGDB keywords bilerek eşlenmez). */
    static Optional<GameProvider.ItemEvidence> evidenceOf(IgdbResponses.Game game) {
        String type = IgdbDetailMapper.providerType(game.gameType());
        java.time.LocalDate date = IgdbDetailMapper.lenientReleaseDate(game.firstReleaseDate());
        List<GameProvider.TermEvidence> genres = IgdbDetailMapper.termEvidence(game.genres());
        List<GameProvider.TermEvidence> platforms = IgdbDetailMapper.termEvidence(game.platforms());
        List<GameProvider.TermEvidence> categories = IgdbDetailMapper.termEvidence(game.gameModes());
        List<GameProvider.TermEvidence> tags = IgdbDetailMapper.termEvidence(game.themes());
        boolean any = type != null || date != null || !genres.isEmpty() || !platforms.isEmpty() || !categories.isEmpty() || !tags.isEmpty();
        return any ? Optional.of(new GameProvider.ItemEvidence(type, date, genres, platforms, categories, tags)) : Optional.empty();
    }


    /** Geçersiz/aralık dışı veri yalnız o oyunu kanıtsız bırakır; sayfa bozulmaz. */
    static Optional<GameProvider.Details> detailsOf(IgdbResponses.Game game) {
        try {
            return Optional.of(IgdbDetailMapper.toDetails(game));
        } catch (ProviderException error) {
            LOG.debug("IGDB oyun detayı atlandı: kod={}", error.code());
            return Optional.empty();
        }
    }


    static GameProvider.RelationEvidence relationOf(IgdbResponses.Game game, Map<Long, Set<String>> steamOfParent) {
        Integer type = game.gameType() != null && game.gameType().intValue() == game.gameType() ? game.gameType().intValue() : null;
        if (game.parentGame() == null) {
            return new GameProvider.RelationEvidence(type, GameProvider.ParentState.NONE, null);
        }
        Set<String> uids = steamOfParent.getOrDefault(game.parentGame(), Set.of());
        if (uids.size() == 1) {
            return new GameProvider.RelationEvidence(type, GameProvider.ParentState.LINKED, uids.iterator().next());
        }
        return new GameProvider.RelationEvidence(type, uids.isEmpty() ? GameProvider.ParentState.UNMAPPED : GameProvider.ParentState.AMBIGUOUS, null);
    }

}

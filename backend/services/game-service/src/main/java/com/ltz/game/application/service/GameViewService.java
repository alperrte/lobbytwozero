package com.ltz.game.application.service;

import com.ltz.game.api.dto.response.GameCardResponse;
import com.ltz.game.api.dto.response.GameDetailResponse;
import com.ltz.game.api.dto.response.GameDlcPageResponse;
import com.ltz.game.api.dto.response.GameRelationResponse;
import com.ltz.game.application.mapper.GameViewMapper;
import com.ltz.game.domain.entity.Game;
import com.ltz.game.domain.enums.GameType;
import com.ltz.game.domain.exception.GameNotFoundException;
import com.ltz.game.domain.exception.InvalidGameRequestException;
import com.ltz.game.infrastructure.persistence.repository.GameLocalizedDescriptionRepository;
import com.ltz.game.infrastructure.persistence.repository.GameRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class GameViewService {
    private final GameRepository games;
    private final GameViewMapper mapper;
    private final GameLocalizedDescriptionRepository localizedDescriptions;
    private final RefreshHints hints;
    public GameCardResponse card(UUID id) { return mapper.card(active(id)); }
    public GameDetailResponse detail(UUID id) { return detail(id, null); }

    /**
     * Detay, istenen dile uygun açıklamayla. Dil, yalnız sağlayıcının o dilde GERÇEKTEN verdiği metin varsa uygulanır (çeviri yok); yoksa
     * varsayılan (İngilizce) açıklama ve gerçek dili ({@code descriptionLanguage}) döner. Geçersiz dil kodu 400.
     */
    public GameDetailResponse detail(UUID id, String language) {
        Game game = active(id);
        // Oyun sayfası açıldı: bellek içi ipucu (dış servise gidilmez, yazılmaz); çıkış durumu ve detay arka planda tazelenir.
        hints.touch(game.getId(), null);
        var response = mapper.detail(game).withRelation(relation(game));
        String code = normalizeLanguage(language);
        if (code != null && !DEFAULT_LANGUAGE.equals(code)) {
            var localized = localizedDescriptions.findByGameAndLanguage(id, code);
            if (localized.isPresent()) return response.withDescription(localized.get().getDescription(), code);
        }
        return response;
    }

    static final String DEFAULT_LANGUAGE = "en";

    private static String normalizeLanguage(String value) {
        if (value == null || value.isBlank()) return null;
        String code = value.strip().toLowerCase(java.util.Locale.ROOT);
        if (!code.matches("[a-z]{2,3}")) throw new InvalidGameRequestException("INVALID_LANGUAGE");
        return code;
    }

    /** DLC: yalnız güvenilir (aktif MAIN_GAME) ana oyun gösterilir; MAIN_GAME: aktif DLC sayısı; diğer türler: ilişki yok. */
    private GameRelationResponse relation(Game game) {
        if (game.getGameType() == GameType.DLC) {
            var parent = game.getParentGameId() == null ? null : games.findById(game.getParentGameId())
                    .filter(Game::isActive).filter(candidate -> candidate.getGameType() == GameType.MAIN_GAME)
                    .map(candidate -> new GameRelationResponse.Parent(candidate.getId(), candidate.getTitle(), mapper.card(candidate).coverUrl()))
                    .orElse(null);
            return new GameRelationResponse(parent, null);
        }
        if (game.getGameType() == GameType.MAIN_GAME) {
            return new GameRelationResponse(null, games.activeDlcOf(game.getId(), PageRequest.of(0, 1)).getTotalElements());
        }
        return null;
    }

    /** Bir ana oyunun aktif DLC'leri (sayfalı). Ana oyun olmayan veya bulunamayan kayıt: 404 / boş sayfa. */
    public GameDlcPageResponse dlc(UUID id, int page, int size) {
        if (page < 0 || page > 10000 || size < 1 || size > 50) throw new InvalidGameRequestException("INVALID_PAGE");
        Game game = active(id);
        if (game.getGameType() != GameType.MAIN_GAME) return new GameDlcPageResponse(List.of(), page, size, 0);
        Page<Game> result = games.activeDlcOf(id, PageRequest.of(page, size));
        return new GameDlcPageResponse(result.getContent().stream().map(dlc -> new GameDlcPageResponse.Item(mapper.card(dlc), dlc.getReleaseDate())).toList(),
                page, size, result.getTotalElements());
    }
    private Game active(UUID id) {
        return games.findById(id).filter(Game::isActive).orElseThrow(GameNotFoundException::new);
    }
}

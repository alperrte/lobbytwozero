package com.ltz.game.application.service;

import com.ltz.game.domain.entity.Game;
import com.ltz.game.domain.entity.GameMedia;
import com.ltz.game.domain.enums.MediaType;
import java.util.Comparator;

/** Sağlayıcı kapağını yazar: yönetici kapağı hiç ezilmez; Steam başlık görseli IGDB kapağının yerine geçer, tersi olmaz. */
final class GameCoverWriter {
    private GameCoverWriter() { }

    /** @return kapak oluşturuldu veya güncellendiyse true */
    static boolean apply(Game game, String url, String provider) {
        if (url == null || !ProviderCoverPolicy.accepts(url)) return false;
        GameMedia existing = game.getMedia().stream().filter(media -> media.getType() == MediaType.COVER)
                .min(Comparator.comparingInt(GameMedia::getSortOrder).thenComparing(GameMedia::getId)).orElse(null);
        if (existing == null) {
            GameMedia cover = new GameMedia();
            cover.setGame(game);
            cover.setType(MediaType.COVER);
            cover.setUrl(url);
            cover.setProvider(provider);
            cover.setSortOrder(0);
            game.getMedia().add(cover);
            return true;
        }
        if (ProviderCoverPolicy.rank(existing.getProvider()) >= ProviderCoverPolicy.rank(provider)) return false;
        existing.setUrl(url);
        existing.setProvider(provider);
        return true;
    }
}

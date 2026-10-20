package com.ltz.game.application.mapper;

import com.ltz.game.api.dto.response.*;
import com.ltz.game.domain.entity.Game;
import com.ltz.game.domain.entity.GameExternalReference;
import com.ltz.game.domain.entity.GameMedia;
import com.ltz.game.domain.enums.MediaType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Comparator;

@Component @RequiredArgsConstructor
public class GameViewMapper {
    private final GameMetadataMapper metadata;

    public GameCardResponse card(Game game) {
        var references = game.getExternalReferences().stream()
                .sorted(Comparator.comparing(GameExternalReference::getProvider).thenComparing(GameExternalReference::getExternalId))
                .map(r -> new GameCardResponse.ExternalReference(r.getProvider(), r.getExternalId(), r.getLastSyncedAt())).toList();
        String cover = game.getMedia().stream().filter(m -> m.getType() == MediaType.COVER)
                .min(Comparator.comparingInt(GameMedia::getSortOrder).thenComparing(GameMedia::getId))
                .map(GameMedia::getUrl).orElse(null);
        return new GameCardResponse(game.getId(), game.getTitle(), game.getOrigin(), game.getMetadataCompleteness(), game.getGameType(), references, cover);
    }

    public GameDetailResponse detail(Game game) {
        return new GameDetailResponse(card(game), metadata.toResponse(game));
    }
}

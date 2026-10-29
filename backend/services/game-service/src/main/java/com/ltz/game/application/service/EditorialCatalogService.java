package com.ltz.game.application.service;

import com.ltz.game.domain.entity.Game;
import com.ltz.game.domain.enums.GameType;
import com.ltz.game.infrastructure.persistence.repository.GameProviderSyncStateRepository;
import com.ltz.game.infrastructure.persistence.repository.GameRepository;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Yalnız iç uygulama komutu; doğrulanmış yönetim girişi Faz 4 kapsamındadır. */
@Service @RequiredArgsConstructor
public class EditorialCatalogService {
    private final GameRepository games;
    private final GameProviderSyncStateRepository states;
    private final Clock clock;
    @Transactional
    public void replace(List<UUID> orderedIds,UUID actorId,String evidence) {
        validate(orderedIds,actorId,evidence);
        List<UUID> ids=List.copyOf(orderedIds);
        states.lockProvider("STEAM").orElseThrow();
        if (!ids.isEmpty()) {
            var selected=games.lockEditorialGames(ids);
            if (selected.size()!=ids.size() || selected.stream().anyMatch(game -> !game.isActive() || game.getGameType()!=GameType.MAIN_GAME))
                throw new IllegalArgumentException("Yalnız aktif ana oyunlar seçilebilir.");
        }
        games.clearEditorialSelection();
        Map<UUID,Game> selected=games.findAllById(ids).stream().collect(Collectors.toMap(Game::getId,Function.identity()));
        for (int index=0;index<ids.size();index++) {
            Game game=selected.get(ids.get(index));
            game.setEditorRank(index+1); game.setPopularityUpdatedAt(clock.instant());
            game.setEditorActorId(actorId); game.setEditorEvidence(evidence.strip());
        }
        games.flush();
    }
    private void validate(List<UUID> ids,UUID actor,String evidence) {
        if (ids==null || ids.size()>100 || ids.stream().anyMatch(Objects::isNull)
                || new HashSet<>(ids).size()!=ids.size() || actor==null || evidence==null
                || evidence.strip().length()<8 || evidence.length()>1000)
            throw new IllegalArgumentException("Geçersiz editör seçimi.");
    }
}

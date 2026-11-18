package com.ltz.game.application.service;

import com.ltz.game.api.dto.response.RecommendationMetadataBatchResponse;
import com.ltz.game.api.dto.response.RecommendationMetadataBatchResponse.Item;
import com.ltz.game.domain.enums.GameType;
import com.ltz.game.infrastructure.persistence.repository.RecommendationMetadataBatchRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecommendationMetadataBatchService {
    private final RecommendationMetadataBatchRepository repository;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 5)
    public RecommendationMetadataBatchResponse read(List<UUID> requested) {
        var eligible = repository.eligibleGames(requested);
        if (eligible.isEmpty()) {
            return new RecommendationMetadataBatchResponse(List.of(), requested);
        }
        var terms = repository.terms(eligible.keySet());
        var requirements = repository.requirements(eligible.keySet());
        List<Item> items = requested.stream().filter(eligible::containsKey)
                .map(id -> new Item(id, GameType.MAIN_GAME, false, true, terms.genres().getOrDefault(id, List.of()),
                        terms.platforms().getOrDefault(id, List.of()), requirements.getOrDefault(id, List.of()),
                        eligible.get(id)))
                .toList();
        return new RecommendationMetadataBatchResponse(items, requested.stream().filter(id -> !eligible.containsKey(id)).toList());
    }
}

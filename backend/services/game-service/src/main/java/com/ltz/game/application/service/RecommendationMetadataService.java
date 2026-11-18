package com.ltz.game.application.service;

import com.ltz.game.api.dto.response.RecommendationMetadataBatchResponse;
import com.ltz.game.infrastructure.persistence.repository.RecommendationMetadataRepository;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** REC-06/16: aday oyunların tür/platform ve sistem gereksinimlerini tek istekte verir. Yalnız herkese açık katalog verisi. */
@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class RecommendationMetadataService {
    static final int MAX_IDS = 100;
    private final RecommendationMetadataRepository repository;

    public RecommendationMetadataBatchResponse batch(List<UUID> requested) {
        if (requested == null || requested.isEmpty() || requested.size() > MAX_IDS || requested.stream().anyMatch(java.util.Objects::isNull)
                || new HashSet<>(requested).size() != requested.size())
            throw new IllegalArgumentException("Geçersiz toplu istek.");
        var found = repository.visible(requested);
        return new RecommendationMetadataBatchResponse(requested.stream().map(found::get).filter(java.util.Objects::nonNull).toList(),
                requested.stream().filter(id -> !found.containsKey(id)).toList());
    }
}

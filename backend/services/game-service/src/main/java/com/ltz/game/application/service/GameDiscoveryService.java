package com.ltz.game.application.service;

import com.ltz.game.api.dto.response.GameDiscoveryResponse;
import com.ltz.game.domain.exception.CatalogRatingUnavailableException;
import com.ltz.game.infrastructure.persistence.repository.GameDiscoveryRepository;
import com.ltz.game.infrastructure.persistence.repository.GameRatingProjectionRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class GameDiscoveryService {
    private final GameDiscoveryRepository repository;
    private final GameRatingProjectionRepository ratings;
    // Count, sayfa, kart ilişkileri ve puan projeksiyonu durumu tek MVCC görünümünü kullanır: puan filtresi, sıralaması ve
    // gösterilen puan durumu aynı projeksiyondan gelir.
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public GameDiscoveryResponse list(DiscoveryQuery query) {
        Instant syncedAt = ratings.lastCompletedAt().orElse(null);
        // İlk hatasız tarama bitmeden boş projeksiyon "kimsenin puanı yok" sayılmaz; puan filtresi/sıralaması kapalı kalır.
        if (query.ratingBased() && syncedAt == null) throw new CatalogRatingUnavailableException();
        return repository.discover(query,syncedAt);
    }
}

package com.ltz.review.application.service;

import com.ltz.review.api.dto.response.RecommendationResponse;
import com.ltz.review.application.port.GameCatalogPort;
import com.ltz.review.domain.exception.ReviewException;
import com.ltz.review.infrastructure.persistence.repository.RecommendationRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RecommendationService {
    private final GameCatalogPort games;
    private final RecommendationWriter writer;
    private final RecommendationRepository recommendations;

    public RecommendationService(GameCatalogPort games, RecommendationWriter writer, RecommendationRepository recommendations) {
        this.games = games;
        this.writer = writer;
        this.recommendations = recommendations;
    }

    public RecommendationResponse recommend(UUID userId, UUID gameId, boolean recommended) {
        games.requireAvailable(gameId);
        return writer.put(userId, gameId, recommended);
    }

    public RecommendationResponse get(UUID userId, UUID gameId) {
        return recommendations.find(userId, gameId).orElseThrow(() -> new ReviewException(404, "Tavsiye bulunamadı."));
    }
}

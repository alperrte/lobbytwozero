package com.ltz.review.application.service;

import com.ltz.review.api.dto.response.RecommendationResponse;
import com.ltz.review.infrastructure.persistence.repository.RecommendationRepository;
import com.ltz.review.infrastructure.persistence.repository.AccountWriteLock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecommendationWriter {
    private final RecommendationRepository recommendations;
    private final AccountWriteLock accounts;

    public RecommendationWriter(RecommendationRepository recommendations, AccountWriteLock accounts) {
        this.recommendations = recommendations;
        this.accounts = accounts;
    }

    @Transactional(timeout = 4)
    public RecommendationResponse put(UUID userId, UUID gameId, boolean recommended) {
        accounts.requireWritable(userId);
        recommendations.put(userId, gameId, recommended);
        return recommendations.find(userId, gameId).orElseThrow();
    }
}

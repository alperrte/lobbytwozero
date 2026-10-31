package com.ltz.review.application.service;

import com.ltz.review.api.dto.response.GameReviewStatisticsResponse;
import com.ltz.review.infrastructure.persistence.repository.ReviewStatisticsRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewStatisticsService {
    private final ReviewStatisticsRepository statistics;

    public ReviewStatisticsService(ReviewStatisticsRepository statistics) { this.statistics = statistics; }

    @Transactional(readOnly = true, timeout = 4)
    public GameReviewStatisticsResponse get(UUID gameId) { return statistics.find(gameId); }
}

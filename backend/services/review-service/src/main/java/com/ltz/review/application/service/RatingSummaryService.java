package com.ltz.review.application.service;

import com.ltz.review.api.dto.response.GameRatingSummaryResponse;
import com.ltz.review.api.dto.response.RatingSummariesResponse;
import com.ltz.review.infrastructure.persistence.repository.RatingSummaryRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RatingSummaryService {
    private final RatingSummaryRepository summaries;

    public RatingSummaryService(RatingSummaryRepository summaries) { this.summaries = summaries; }

    @Transactional(readOnly = true, timeout = 4)
    public GameRatingSummaryResponse get(UUID gameId) { return summaries.find(gameId); }

    @Transactional(readOnly = true, timeout = 4)
    public RatingSummariesResponse getAll(List<UUID> gameIds) {
        return new RatingSummariesResponse(summaries.findAll(List.copyOf(new LinkedHashSet<>(gameIds))));
    }
}

package com.ltz.recommendation.api.dto.response;

import com.ltz.recommendation.application.port.RecommendationHistory.Run;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RecommendationRunResponse(UUID runId, String algorithmVersion, Instant createdAt, String status,
        String genre, String platform, List<Item> items, List<String> limitations) {
    public static RecommendationRunResponse from(Run run) {
        return new RecommendationRunResponse(run.runId(), run.algorithmVersion(), run.createdAt(),
                run.items().isEmpty() ? "EMPTY" : "PARTIAL", run.filters().genre(), run.filters().platform(),
                run.items().stream().map(item -> new Item(item.itemId(), item.gameId(), item.score(), item.rank(),
                        "GENRE_RATING_AFFINITY", item.score(), item.matchedGenres())).toList(),
                List.of("BOUNDED_DISCOVERY_50_PER_GENRE"));
    }

    public record Item(UUID itemId, UUID gameId, int score, int rank, String reasonCode,
            int contribution, List<String> matchedGenres) { }
}

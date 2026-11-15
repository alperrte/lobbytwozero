package com.ltz.recommendation.application.port;

import com.ltz.recommendation.application.port.PersonalizationSource.Filters;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface RecommendationHistory {
    void lockAccount(UUID accountId);
    Run save(UUID owner, Run result);
    Run read(UUID owner, UUID runId);
    Page history(UUID owner, UUID beforeRunId, int size);
    Feedback feedback(UUID owner, UUID runId, UUID itemId, FeedbackValue value, long version);
    Feedback feedbackState(UUID owner, UUID runId, UUID itemId);
    java.util.Set<UUID> notInterested(UUID owner);
    void erase(UUID owner);

    record StoredItem(UUID itemId, UUID gameId, int score, int rank, List<String> matchedGenres) { }
    record Run(UUID runId, String algorithmVersion, Instant createdAt, Filters filters, List<StoredItem> items) { }
    record Summary(UUID runId, String algorithmVersion, Instant createdAt, int resultCount) { }
    record Page(List<Summary> items, UUID nextCursor) { }
    enum FeedbackValue { LIKE, NOT_INTERESTED }
    record Feedback(UUID itemId, FeedbackValue value, long version) { }
}

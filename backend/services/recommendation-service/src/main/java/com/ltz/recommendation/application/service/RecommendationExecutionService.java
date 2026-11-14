package com.ltz.recommendation.application.service;

import com.ltz.recommendation.application.port.RecommendationAlgorithm;
import com.ltz.recommendation.application.port.RecommendationAlgorithm.Input;
import com.ltz.recommendation.application.port.RecommendationAlgorithm.Item;
import com.ltz.recommendation.application.port.RecommendationAnalytics;
import com.ltz.recommendation.application.port.RecommendationAnalytics.Observation;
import com.ltz.recommendation.domain.enums.RecommendationOutcome;
import com.ltz.recommendation.domain.exception.RecommendationUnavailableException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RecommendationExecutionService {
    private final RecommendationAlgorithm algorithm;
    private final RecommendationAnalytics analytics;

    public RecommendationExecutionService(RecommendationAlgorithm algorithm, RecommendationAnalytics analytics) {
        this.algorithm = algorithm;
        this.analytics = analytics;
    }

    public Result execute(Input input) {
        String version = algorithm.version();
        if (!version.equals(input.algorithmVersion())) {
            throw new IllegalArgumentException("Unsupported algorithm version");
        }
        UUID runId = UUID.randomUUID();
        Instant occurredAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        List<Item> items;
        try {
            items = List.copyOf(algorithm.rank(input));
            validate(input, items);
        } catch (RecommendationUnavailableException exception) {
            analytics.record(new Observation(runId, version, occurredAt, RecommendationOutcome.UNAVAILABLE,
                    input.candidates().size(), 0, 0));
            throw exception;
        }
        analytics.record(new Observation(runId, version, occurredAt,
                items.isEmpty() ? RecommendationOutcome.EMPTY : RecommendationOutcome.GENERATED,
                input.candidates().size(), items.size(), items.stream().mapToInt(Item::score).sum()));
        return new Result(runId, version, occurredAt, items);
    }

    private static void validate(Input input, List<Item> items) {
        if (items.size() > input.limit() || items.stream().map(Item::targetId).distinct().count() != items.size()) {
            throw new IllegalStateException("Algorithm result violates contract");
        }
        int previousScore = 10001;
        int expectedRank = 1;
        for (Item item : items) {
            if (item.rank() != expectedRank++ || item.score() > previousScore
                    || input.candidates().stream().noneMatch(candidate -> candidate.targetId().equals(item.targetId()))) {
                throw new IllegalStateException("Algorithm result violates contract");
            }
            previousScore = item.score();
        }
    }

    public record Result(UUID runId, String algorithmVersion, Instant occurredAt, List<Item> items) {
        public Result {
            items = List.copyOf(items);
        }
    }
}

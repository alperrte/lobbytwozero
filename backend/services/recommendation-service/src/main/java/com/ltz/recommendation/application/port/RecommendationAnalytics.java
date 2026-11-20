package com.ltz.recommendation.application.port;

import com.ltz.recommendation.domain.enums.RecommendationOutcome;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

public interface RecommendationAnalytics {
    void record(Observation observation);

    /** Yalnız sunucunun tamamladığı çalışmayı ölçer; kullanıcı/hedef/sinyal içermez. */
    record Observation(UUID runId, String algorithmVersion, Instant occurredAt,
            RecommendationOutcome outcome, int candidateCount, int resultCount, int totalScore) {
        public Observation {
            Objects.requireNonNull(runId, "runId");
            Objects.requireNonNull(occurredAt, "occurredAt");
            occurredAt = occurredAt.truncatedTo(ChronoUnit.MICROS);
            Objects.requireNonNull(outcome, "outcome");
            if (algorithmVersion == null || !algorithmVersion.matches("[a-z][a-z0-9-]{0,31}")) {
                throw new IllegalArgumentException("Invalid algorithm version");
            }
            if (candidateCount < 0 || candidateCount > 500 || resultCount < 0
                    || resultCount > Math.min(50, candidateCount) || totalScore < resultCount
                    || totalScore > resultCount * 10000
                    || (outcome == RecommendationOutcome.GENERATED) != (resultCount > 0)) {
                throw new IllegalArgumentException("Invalid observation counts");
            }
        }
    }
}

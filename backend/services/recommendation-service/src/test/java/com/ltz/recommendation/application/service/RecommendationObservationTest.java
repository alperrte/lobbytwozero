package com.ltz.recommendation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ltz.recommendation.application.port.RecommendationAnalytics.Observation;
import com.ltz.recommendation.domain.enums.RecommendationOutcome;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecommendationObservationTest {
    @Test
    void validatesVersionCountsAndOutcomeSemantics() {
        for (String version : new String[] {null, "", "private data", "x".repeat(33)}) {
            assertThatThrownBy(() -> observation(version, RecommendationOutcome.EMPTY, 0, 0, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        for (int[] counts : new int[][] {{-1, 0, 0}, {501, 0, 0}, {1, -1, 0}, {1, 2, 2},
                {100, 51, 51}, {1, 1, 10001}, {1, 1, 0}}) {
            assertThatThrownBy(() -> observation("rules-v1", RecommendationOutcome.GENERATED, counts[0], counts[1], counts[2]))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> observation("rules-v1", RecommendationOutcome.EMPTY, 1, 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> observation("rules-v1", RecommendationOutcome.GENERATED, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(observation("rules-v1", RecommendationOutcome.UNAVAILABLE, 5, 0, 0).resultCount()).isZero();
    }

    private static Observation observation(String version, RecommendationOutcome outcome, int candidates, int results, int score) {
        return new Observation(UUID.randomUUID(), version, Instant.now(), outcome, candidates, results, score);
    }
}

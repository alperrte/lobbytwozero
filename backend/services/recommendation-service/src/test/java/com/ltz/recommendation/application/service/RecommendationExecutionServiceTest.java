package com.ltz.recommendation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ltz.recommendation.application.port.RecommendationAlgorithm;
import com.ltz.recommendation.application.port.RecommendationAlgorithm.Input;
import com.ltz.recommendation.application.port.RecommendationAlgorithm.Item;
import com.ltz.recommendation.application.port.RecommendationAlgorithm.Reason;
import com.ltz.recommendation.application.port.RecommendationAlgorithm.Signal;
import com.ltz.recommendation.application.port.RecommendationAnalytics;
import com.ltz.recommendation.domain.enums.RecommendationOutcome;
import com.ltz.recommendation.domain.exception.RecommendationUnavailableException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RecommendationExecutionServiceTest {
    private final List<RecommendationAnalytics.Observation> observations = new ArrayList<>();
    private final RecommendationAnalytics analytics = observations::add;

    @Test
    void realEngineProducesVersionedRunAndServerObservedMetrics() {
        var service = new RecommendationExecutionService(new RuleBasedRecommendationEngine(), analytics);
        var result = service.execute(RuleBasedRecommendationEngineTest.input(List.of(
                RuleBasedRecommendationEngineTest.candidate(1, Map.of(Signal.GENRE_AFFINITY, 10000))), 1));
        assertThat(observations).hasSize(1);
        assertThat(observations.getFirst().runId()).isEqualTo(result.runId());
        assertThat(observations.getFirst().outcome()).isEqualTo(RecommendationOutcome.GENERATED);
        assertThat(observations.getFirst().totalScore()).isEqualTo(2000);
        assertThat(result.algorithmVersion()).isEqualTo("rules-v1");
        assertThat(result.items()).hasSize(1);
    }

    @Test
    void emptyResultIsRecordedAsEmptyNotAnImpression() {
        new RecommendationExecutionService(new RuleBasedRecommendationEngine(), analytics)
                .execute(RuleBasedRecommendationEngineTest.input(List.of(), 1));
        assertThat(observations.getFirst().outcome()).isEqualTo(RecommendationOutcome.EMPTY);
        assertThat(observations.getFirst().resultCount()).isZero();
    }

    @Test
    void alternativeAlgorithmUsesSameContractWithoutChangingService() {
        var alternative = new TestAlgorithm("alternative-v2", List.of(), false);
        Input input = new Input("alternative-v2", List.of(), 1);
        assertThat(new RecommendationExecutionService(alternative, analytics).execute(input).algorithmVersion())
                .isEqualTo("alternative-v2");
    }

    @Test
    void unavailableAlgorithmRecordsFailureAndDoesNotInventFallback() {
        var alternative = new TestAlgorithm("rules-v1", List.of(), true);
        assertThatThrownBy(() -> new RecommendationExecutionService(alternative, analytics)
                .execute(RuleBasedRecommendationEngineTest.input(List.of(), 1)))
                .isInstanceOf(RecommendationUnavailableException.class);
        assertThat(observations.getFirst().outcome()).isEqualTo(RecommendationOutcome.UNAVAILABLE);
    }

    @Test
    void versionMismatchDoesNotExecuteOrRecordAnything() {
        var algorithm = new TestAlgorithm("rules-v1", List.of(), true);
        assertThatThrownBy(() -> new RecommendationExecutionService(algorithm, analytics)
                .execute(new Input("other-v2", List.of(), 1))).isInstanceOf(IllegalArgumentException.class);
        assertThat(observations).isEmpty();
    }

    @Test
    void analyticsFailureDoesNotReturnAnUnrecordedSuccess() {
        RecommendationAnalytics unavailable = observation -> { throw new IllegalStateException("Test storage unavailable"); };
        assertThatThrownBy(() -> new RecommendationExecutionService(new RuleBasedRecommendationEngine(), unavailable)
                .execute(RuleBasedRecommendationEngineTest.input(List.of(), 1))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsUnknownTargetsIncorrectRankAndDuplicateResultsFromAdapter() {
        Input input = RuleBasedRecommendationEngineTest.input(List.of(
                RuleBasedRecommendationEngineTest.candidate(1, Map.of())), 2);
        for (List<Item> invalid : List.of(List.of(item(2, 1)), List.of(item(1, 2)),
                List.of(item(1, 1), item(1, 2)))) {
            var algorithm = new TestAlgorithm("rules-v1", invalid, false);
            assertThatThrownBy(() -> new RecommendationExecutionService(algorithm, analytics).execute(input))
                    .isInstanceOf(IllegalStateException.class);
        }
        assertThat(observations).isEmpty();
    }

    private static Item item(int id, int rank) {
        return new Item(RuleBasedRecommendationEngineTest.id(id), 100, rank, List.of(new Reason(Signal.GENRE_AFFINITY, 100)));
    }

    private record TestAlgorithm(String version, List<Item> items, boolean unavailable) implements RecommendationAlgorithm {
        @Override
        public List<Item> rank(Input input) {
            if (unavailable) throw new RecommendationUnavailableException();
            return items;
        }
    }
}

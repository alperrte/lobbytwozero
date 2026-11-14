package com.ltz.recommendation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ltz.recommendation.application.port.RecommendationAlgorithm.Candidate;
import com.ltz.recommendation.application.port.RecommendationAlgorithm.Input;
import com.ltz.recommendation.application.port.RecommendationAlgorithm.Item;
import com.ltz.recommendation.application.port.RecommendationAlgorithm.Reason;
import com.ltz.recommendation.application.port.RecommendationAlgorithm.Signal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RuleBasedRecommendationEngineTest {
    private final RuleBasedRecommendationEngine engine = new RuleBasedRecommendationEngine();

    @Test
    void emptyAndMissingSignalsDoNotInventRecommendations() {
        assertThat(engine.rank(input(List.of(), 10))).isEmpty();
        assertThat(engine.rank(input(List.of(candidate(1, Map.of()),
                candidate(2, Map.of(Signal.GENRE_AFFINITY, 0))), 10))).isEmpty();
    }

    @Test
    void oneCandidateHasExactScoreAndReasonsInStableOrder() {
        var result = engine.rank(input(List.of(candidate(1, Map.of(Signal.FAVORITE_AFFINITY, 10000,
                Signal.RATING_AFFINITY, 5000, Signal.GENRE_AFFINITY, 10000,
                Signal.COMPLETED_AFFINITY, 10000))), 10));
        assertThat(result).containsExactly(new Item(id(1), 8500, 1, List.of(
                new Reason(Signal.FAVORITE_AFFINITY, 4000), new Reason(Signal.RATING_AFFINITY, 1500),
                new Reason(Signal.GENRE_AFFINITY, 2000), new Reason(Signal.COMPLETED_AFFINITY, 1000))));
    }

    @Test
    void manyCandidatesHaveStableScoresTiesAndLimitRegardlessOfInputOrder() {
        List<Candidate> candidates = new ArrayList<>(List.of(
                candidate(3, Map.of(Signal.FAVORITE_AFFINITY, 10000)),
                candidate(2, Map.of(Signal.GENRE_AFFINITY, 10000)),
                candidate(1, Map.of(Signal.FAVORITE_AFFINITY, 10000))));
        var expected = engine.rank(input(candidates, 2));
        for (int index = 0; index < 20; index++) {
            Collections.rotate(candidates, 1);
            assertThat(engine.rank(input(candidates, 2))).isEqualTo(expected);
        }
        assertThat(expected).extracting(Item::targetId).containsExactly(id(1), id(3));
        assertThat(expected).extracting(Item::rank).containsExactly(1, 2);
    }

    @Test
    void maximumScoreIsBoundedAndRoundingDoesNotProduceUnsupportedReasons() {
        var all = Map.of(Signal.FAVORITE_AFFINITY, 10000, Signal.RATING_AFFINITY, 10000,
                Signal.GENRE_AFFINITY, 10000, Signal.COMPLETED_AFFINITY, 10000);
        assertThat(engine.rank(input(List.of(candidate(1, all)), 1)).getFirst().score()).isEqualTo(10000);
        assertThat(engine.rank(input(List.of(candidate(1, Map.of(Signal.COMPLETED_AFFINITY, 1))), 1))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 10001})
    void invalidSignalsAreRejected(int value) {
        assertThatThrownBy(() -> candidate(1, Map.of(Signal.GENRE_AFFINITY, value)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 51})
    void invalidLimitsAreRejected(int limit) {
        assertThatThrownBy(() -> input(List.of(), limit)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDuplicateOversizedAndNullCandidates() {
        Candidate candidate = candidate(1, Map.of());
        assertThatThrownBy(() -> input(List.of(candidate, candidate), 1)).isInstanceOf(IllegalArgumentException.class);
        List<Candidate> tooMany = new ArrayList<>();
        for (int index = 0; index < 501; index++) tooMany.add(candidate(index, Map.of()));
        assertThatThrownBy(() -> input(tooMany, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Candidate(null, Map.of())).isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsUnknownVersionAndInconsistentExplanations() {
        assertThatThrownBy(() -> engine.rank(new Input("rules-v2", List.of(), 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Reason(Signal.GENRE_AFFINITY, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Item(id(1), 100, 1, List.of(new Reason(Signal.GENRE_AFFINITY, 50))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Item(id(1), 100, 1, List.of(new Reason(Signal.GENRE_AFFINITY, 50),
                new Reason(Signal.GENRE_AFFINITY, 50)))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void inputsAndOutputsAreImmutable() {
        List<Candidate> candidates = new ArrayList<>(List.of(candidate(1, Map.of(Signal.GENRE_AFFINITY, 10000))));
        Input input = input(candidates, 1);
        candidates.clear();
        assertThat(engine.rank(input)).hasSize(1);
        assertThatThrownBy(() -> engine.rank(input).clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    static UUID id(int index) { return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(index)); }
    static Candidate candidate(int index, Map<Signal, Integer> signals) { return new Candidate(id(index), signals); }
    static Input input(List<Candidate> candidates, int limit) { return new Input("rules-v1", candidates, limit); }
}

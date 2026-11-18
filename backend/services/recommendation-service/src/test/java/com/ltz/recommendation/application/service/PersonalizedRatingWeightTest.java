package com.ltz.recommendation.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ltz.recommendation.application.port.PersonalizationSource.Match;
import com.ltz.recommendation.application.port.RecommendationAlgorithm.Input;
import com.ltz.recommendation.application.port.RecommendationAlgorithm.Signal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PersonalizedRatingWeightTest {
    private static final UUID LIKED = new UUID(0, 1);
    private static final UUID PLAIN = new UUID(0, 2);

    @Test
    void ratedGenresRaiseTheRankWhileUnratedCandidatesStayOnGenreAffinity() {
        var ratings = Map.of("rpg", 8000);
        var liked = PersonalizedRecommendationService.candidate(new Match(LIKED, 5000, List.of("RPG")), ratings);
        var plain = PersonalizedRecommendationService.candidate(new Match(PLAIN, 5000, List.of("racing")), ratings);
        assertThat(liked.signals()).containsEntry(Signal.RATING_AFFINITY, 8000);
        assertThat(plain.signals()).doesNotContainKey(Signal.RATING_AFFINITY);
        var ranked = new RuleBasedRecommendationEngine().rank(new Input("rules-v1", List.of(plain, liked), 10));
        assertThat(ranked).extracting(item -> item.targetId()).containsExactly(LIKED, PLAIN);
        assertThat(ranked.getFirst().reasons()).extracting(reason -> reason.signal())
                .containsExactly(Signal.RATING_AFFINITY, Signal.GENRE_AFFINITY);
    }

    @Test
    void ratingAffinityIsTheMeanOverMatchedGenresWithUnratedGenresCountingZero() {
        var candidate = PersonalizedRecommendationService.candidate(new Match(LIKED, 5000, List.of("action", "rpg")), Map.of("rpg", 8000));
        assertThat(candidate.signals()).containsEntry(Signal.RATING_AFFINITY, 4000);
    }
}

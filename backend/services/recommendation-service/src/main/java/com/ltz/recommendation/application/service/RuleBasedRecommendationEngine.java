package com.ltz.recommendation.application.service;

import com.ltz.recommendation.application.port.RecommendationAlgorithm;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RuleBasedRecommendationEngine implements RecommendationAlgorithm {
    private static final Map<Signal, Integer> WEIGHTS = Map.of(
            Signal.FAVORITE_AFFINITY, 40, Signal.RATING_AFFINITY, 30,
            Signal.GENRE_AFFINITY, 20, Signal.COMPLETED_AFFINITY, 10);

    @Override
    public String version() {
        return "rules-v1";
    }

    @Override
    public List<Item> rank(Input input) {
        if (!version().equals(input.algorithmVersion())) {
            throw new IllegalArgumentException("Unsupported algorithm version");
        }
        List<Scored> scored = input.candidates().stream().map(this::score)
                .filter(item -> item.score() > 0)
                .sorted(Comparator.comparingInt(Scored::score).reversed()
                        .thenComparing(item -> item.candidate().targetId().toString()))
                .limit(input.limit())
                .toList();
        List<Item> ranked = new ArrayList<>(scored.size());
        for (Scored item : scored) {
            ranked.add(new Item(item.candidate().targetId(), item.score(), ranked.size() + 1, item.reasons()));
        }
        return List.copyOf(ranked);
    }

    private Scored score(Candidate candidate) {
        List<Reason> reasons = new ArrayList<>();
        for (Signal signal : Signal.values()) {
            int contribution = candidate.signals().getOrDefault(signal, 0) * WEIGHTS.get(signal) / 100;
            if (contribution > 0) {
                reasons.add(new Reason(signal, contribution));
            }
        }
        return new Scored(candidate, reasons.stream().mapToInt(Reason::contribution).sum(), List.copyOf(reasons));
    }

    private record Scored(Candidate candidate, int score, List<Reason> reasons) { }
}

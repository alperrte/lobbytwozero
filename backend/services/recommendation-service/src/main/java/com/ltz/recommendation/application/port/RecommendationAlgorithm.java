package com.ltz.recommendation.application.port;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Servis içinde kullanılan sürümlü algoritma sınırı; HTTP veya güvenilir veri kaynağı değildir. */
public interface RecommendationAlgorithm {
    String version();

    List<Item> rank(Input input);

    enum Signal {
        FAVORITE_AFFINITY, RATING_AFFINITY, GENRE_AFFINITY, COMPLETED_AFFINITY
    }

    record Candidate(UUID targetId, Map<Signal, Integer> signals) {
        public Candidate {
            Objects.requireNonNull(targetId, "targetId");
            signals = Map.copyOf(signals);
            if (signals.values().stream().anyMatch(value -> value < 0 || value > 10000)) {
                throw new IllegalArgumentException("Signal must be between 0 and 10000");
            }
        }
    }

    record Input(String algorithmVersion, List<Candidate> candidates, int limit) {
        public Input {
            Objects.requireNonNull(algorithmVersion, "algorithmVersion");
            candidates = List.copyOf(candidates);
            if (limit < 1 || limit > 50 || candidates.size() > 500) {
                throw new IllegalArgumentException("Candidate or result limit exceeded");
            }
            if (candidates.stream().map(Candidate::targetId).distinct().count() != candidates.size()) {
                throw new IllegalArgumentException("Duplicate candidate");
            }
        }
    }

    record Reason(Signal signal, int contribution) {
        public Reason {
            Objects.requireNonNull(signal, "signal");
            if (contribution < 1 || contribution > 10000) {
                throw new IllegalArgumentException("Invalid contribution");
            }
        }
    }

    record Item(UUID targetId, int score, int rank, List<Reason> reasons) {
        public Item {
            Objects.requireNonNull(targetId, "targetId");
            reasons = List.copyOf(reasons);
            if (score < 1 || score > 10000 || rank < 1 || rank > 50
                    || reasons.stream().mapToInt(Reason::contribution).sum() != score
                    || reasons.stream().map(Reason::signal).distinct().count() != reasons.size()) {
                throw new IllegalArgumentException("Invalid ranking or explanation");
            }
        }
    }
}

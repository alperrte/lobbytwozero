package com.ltz.recommendation.application.port;

import java.util.List;
import java.util.UUID;

public interface PersonalizationSource {
    void requireConsent(Actor actor);
    List<Match> candidates(Actor actor, Filters filters);

    record Actor(UUID accountId, String bearer) {
        @Override public String toString() { return "Actor[credentials=REDACTED]"; }
    }
    record Filters(String genre, String platform, int size) {
        public Filters {
            if (size < 1 || size > 50 || invalid(genre) || invalid(platform)) {
                throw new IllegalArgumentException("Invalid recommendation filters");
            }
        }
        private static boolean invalid(String value) {
            return value != null && (value.isBlank() || value.length() > 64 || value.chars().anyMatch(Character::isISOControl));
        }
    }
    record Match(UUID gameId, int affinity, List<String> matchedGenres) {
        public Match {
            matchedGenres = List.copyOf(matchedGenres);
            if (gameId == null || affinity < 1 || affinity > 10000 || matchedGenres.isEmpty() || matchedGenres.size() > 10) {
                throw new IllegalArgumentException("Invalid catalog match");
            }
        }
    }
}

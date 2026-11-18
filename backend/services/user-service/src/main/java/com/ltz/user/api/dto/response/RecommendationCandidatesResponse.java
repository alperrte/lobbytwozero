package com.ltz.user.api.dto.response;

import java.util.List;
import java.util.UUID;

import com.ltz.user.application.service.RecommendationCandidateService.CandidatePage;

/**
 * Öneri aday sayfası. Yalnız tercih sinyalleri döner; ad, e-posta, kütüphane, puan, ülke ya da başka kişisel alan yoktur.
 *
 * @param nextCursor sonraki sayfa için {@code cursor} değeri; son sayfada {@code null}
 */
public record RecommendationCandidatesResponse(List<Candidate> candidates, UUID nextCursor) {

    /** Tek aday: kimlik ve oyuncu tercih sinyalleri. */
    public record Candidate(UUID userId, List<String> playerTypes, List<String> favoriteGenres,
                            List<String> favoritePlatforms) {
    }

    public static RecommendationCandidatesResponse from(CandidatePage page) {
        return new RecommendationCandidatesResponse(
                page.candidates().stream()
                        .map(profile -> new Candidate(profile.getUserId(), List.copyOf(profile.getPlayerTypes()),
                                List.copyOf(profile.getFavoriteGenres()), List.copyOf(profile.getFavoritePlatforms())))
                        .toList(),
                page.nextCursor());
    }
}

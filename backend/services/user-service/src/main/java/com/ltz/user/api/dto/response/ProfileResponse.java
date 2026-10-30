package com.ltz.user.api.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ltz.user.application.service.ProfileView;

/** Kullanıcının kendi profili. Hesap durumu, e-posta ve kimlik bilgisi içermez. */
public record ProfileResponse(UUID userId, String username, String displayName, String bio, List<String> playerTypes,
                              List<String> favoriteGenres, List<String> favoritePlatforms, String country,
                              Instant onboardingCompletedAt, Instant updatedAt, long version) {

    public static ProfileResponse from(ProfileView view) {
        return new ProfileResponse(view.userId(), view.username(), view.displayName(), view.bio(),
                view.playerTypes(), view.favoriteGenres(), view.favoritePlatforms(), view.country(),
                view.onboardingCompletedAt(), view.updatedAt(), view.version());
    }
}

package com.ltz.user.application.mapper;

import java.util.List;

import com.ltz.user.application.service.ProfileView;
import com.ltz.user.domain.entity.UserProfile;

/** {@link UserProfile} → uygulama katmanı görünümü. Listeler değiştirilemez kopya olarak döner. */
public final class ProfileMapper {

    private ProfileMapper() {
    }

    public static ProfileView toView(UserProfile profile) {
        return new ProfileView(profile.getUserId(), profile.getUsernameSnapshot(), profile.getDisplayName(),
                profile.getBio(), List.copyOf(profile.getPlayerTypes()), List.copyOf(profile.getFavoriteGenres()),
                List.copyOf(profile.getFavoritePlatforms()), profile.getCountry(),
                profile.getOnboardingCompletedAt(), profile.getUpdatedAt(), profile.getVersion());
    }
}

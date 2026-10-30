package com.ltz.user.application.mapper;

import java.util.List;

import com.ltz.user.application.service.MediaView;
import com.ltz.user.application.service.ProfileView;
import com.ltz.user.domain.entity.UserMedia;
import com.ltz.user.domain.entity.UserProfile;
import com.ltz.user.domain.enums.MediaKind;

/** {@link UserProfile} → uygulama katmanı görünümü. Listeler değiştirilemez kopya olarak döner. */
public final class ProfileMapper {

    private ProfileMapper() {
    }

    /** @param currentMedia profilin {@code CURRENT} görselleri (avatar ve kapak, her türden en çok bir tane) */
    public static ProfileView toView(UserProfile profile, List<UserMedia> currentMedia) {
        return new ProfileView(profile.getUserId(), profile.getUsernameSnapshot(), profile.getDisplayName(),
                profile.getBio(), List.copyOf(profile.getPlayerTypes()), List.copyOf(profile.getFavoriteGenres()),
                List.copyOf(profile.getFavoritePlatforms()), profile.getCountry(),
                mediaOf(currentMedia, MediaKind.AVATAR), mediaOf(currentMedia, MediaKind.COVER),
                profile.getOnboardingCompletedAt(), profile.getUpdatedAt(), profile.getVersion());
    }

    /** Güncel görsellerden istenen türünkini görünüme çevirir; yoksa {@code null}. */
    public static MediaView mediaOf(List<UserMedia> media, MediaKind kind) {
        return media.stream().filter(item -> item.getKind() == kind).findFirst()
                .map(item -> new MediaView(item.smallFileName() != null ? item.smallFileName() : item.largeFileName(),
                        item.largeFileName(), item.isAnimated()))
                .orElse(null);
    }
}

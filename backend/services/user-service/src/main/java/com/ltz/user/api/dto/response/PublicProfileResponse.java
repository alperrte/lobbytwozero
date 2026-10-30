package com.ltz.user.api.dto.response;

import java.util.UUID;

import com.ltz.user.application.service.PublicProfileView;
import com.ltz.user.application.service.PublicProfileView.Details;
import com.ltz.user.application.service.PublicProfileView.Sections;
import com.ltz.user.application.service.PublicProfileView.Summary;

/**
 * Başkasının (ya da misafirin) gördüğü profil. Temel kart her zaman dolu; {@code details} yalnız izin varsa. E-posta,
 * hesap durumu ve kimlik bilgisi içermez. {@code visible}: istemcinin diğer servislere hangi bölümler için gideceği.
 */
public record PublicProfileResponse(UUID userId, String username, String displayName, MediaResponse avatar,
                                    MediaResponse cover, Details details, Sections visible, Summary summary) {

    public static PublicProfileResponse from(PublicProfileView view) {
        return new PublicProfileResponse(view.userId(), view.username(), view.displayName(),
                MediaResponse.from(view.avatar()), MediaResponse.from(view.cover()), view.details(), view.visible(),
                view.summary());
    }
}

package com.ltz.user.api.dto.response;

import com.ltz.user.api.controller.UserApiPaths;
import com.ltz.user.application.service.MediaView;

/**
 * Profil görselinin adresleri. {@code small} liste ve arama sonuçları için, {@code large} profil sayfası içindir; GIF'te
 * ikisi aynı dosyadır. Adresler değişmez (her yükleme yeni adres alır), bu yüzden bir yıl önbelleğe alınabilir.
 */
public record MediaResponse(String small, String large, boolean animated) {

    public static MediaResponse from(MediaView view) {
        if (view == null) {
            return null;
        }
        return new MediaResponse(UserApiPaths.MEDIA + "/" + view.smallFile(), UserApiPaths.MEDIA + "/" + view.largeFile(),
                view.animated());
    }
}

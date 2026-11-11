package com.ltz.messaging.api.dto.response;

import com.ltz.messaging.application.service.GifView;
import com.ltz.messaging.domain.enums.GifProvider;

/** Mesajdaki platform GIF'i (MSG-22). Adresler yalnız sağlayıcının medya sunucularındaki HTTPS adresleridir. */
public record GifResponse(GifProvider provider, String id, String url, String previewUrl, int width, int height) {

    public static GifResponse from(GifView view) {
        return view == null ? null : new GifResponse(view.provider(), view.id(), view.url(), view.previewUrl(),
                view.width(), view.height());
    }
}

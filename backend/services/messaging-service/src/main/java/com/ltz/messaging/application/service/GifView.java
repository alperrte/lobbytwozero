package com.ltz.messaging.application.service;

import com.ltz.messaging.domain.entity.GifSnapshot;
import com.ltz.messaging.domain.enums.GifProvider;

/** Mesajdaki platform GIF'i (MSG-22): gösterilecek HTTPS adresi ve küçük önizleme. */
public record GifView(GifProvider provider, String id, String url, String previewUrl, int width, int height) {

    public static GifView of(GifSnapshot gif) {
        return gif == null ? null : new GifView(gif.getProvider(), gif.getGifId(), gif.getUrl(), gif.getPreviewUrl(),
                gif.getWidth(), gif.getHeight());
    }
}

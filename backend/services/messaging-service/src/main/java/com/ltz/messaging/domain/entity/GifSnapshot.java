package com.ltz.messaging.domain.entity;

import java.util.Objects;

import com.ltz.messaging.domain.enums.GifProvider;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * GIF platformundan seçilen GIF'in mesaja yazılan kopyası (MSG-22). Adresler istemciden değil sağlayıcının yanıtından
 * gelir, yalnız izinli HTTPS sunucularını gösterir ve sorgu dizgesi atılmıştır. GIF dosyası saklanmaz.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GifSnapshot {

    @Enumerated(EnumType.STRING)
    @Column(name = "gif_provider", updatable = false, length = 16)
    private GifProvider provider;

    @Column(name = "gif_id", updatable = false, length = 64)
    private String gifId;

    @Column(name = "gif_url", updatable = false, length = 512)
    private String url;

    @Column(name = "gif_preview_url", updatable = false, length = 512)
    private String previewUrl;

    @Column(name = "gif_width", updatable = false)
    private Integer width;

    @Column(name = "gif_height", updatable = false)
    private Integer height;

    public GifSnapshot(GifProvider provider, String gifId, String url, String previewUrl, int width, int height) {
        this.provider = Objects.requireNonNull(provider);
        this.gifId = Objects.requireNonNull(gifId);
        this.url = Objects.requireNonNull(url);
        this.previewUrl = Objects.requireNonNull(previewUrl);
        this.width = width;
        this.height = height;
    }

    public boolean isSameGif(GifProvider otherProvider, String otherGifId) {
        return provider == otherProvider && gifId.equals(otherGifId);
    }
}

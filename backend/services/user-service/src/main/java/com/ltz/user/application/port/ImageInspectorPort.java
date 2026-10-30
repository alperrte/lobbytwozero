package com.ltz.user.application.port;

import com.ltz.user.domain.enums.MediaKind;

/**
 * Yüklenen görseli doğrular ve sunulacak sürümlere dönüştürür. Güvenilmez girdidir: biçim içerikten (magic byte) belirlenir,
 * bildirilen {@code Content-Type} yalnız tutarlılık için karşılaştırılır. Reddedilen görsel için
 * {@link com.ltz.user.domain.exception.UserException} ({@code INVALID_IMAGE}, {@code INVALID_IMAGE_DIMENSIONS},
 * {@code MEDIA_BUSY}) fırlatılır.
 */
public interface ImageInspectorPort {

    ProcessedImage process(MediaKind kind, byte[] content, String declaredContentType);

    /**
     * İşlenmiş görsel.
     *
     * @param small küçük sürüm (liste ve arama sonuçları için); GIF'te {@code null}
     */
    record ProcessedImage(String extension, String contentType, byte[] large, byte[] small, int width, int height,
                          int frameCount) {
    }
}

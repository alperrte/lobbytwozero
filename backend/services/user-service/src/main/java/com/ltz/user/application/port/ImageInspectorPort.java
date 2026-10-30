package com.ltz.user.application.port;

import java.util.Arrays;
import java.util.Objects;

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

        @Override
        public boolean equals(Object other) {
            return other instanceof ProcessedImage that && Objects.equals(extension, that.extension)
                    && Objects.equals(contentType, that.contentType) && width == that.width
                    && height == that.height && frameCount == that.frameCount && Arrays.equals(large, that.large)
                    && Arrays.equals(small, that.small);
        }

        @Override
        public int hashCode() {
            return 31 * (31 * Objects.hash(extension, contentType, width, height, frameCount)
                    + Arrays.hashCode(large)) + Arrays.hashCode(small);
        }

        @Override
        public String toString() {
            return "ProcessedImage[extension=" + extension + ", contentType=" + contentType + ", large="
                    + large.length + ", small=" + (small == null ? 0 : small.length) + ", width=" + width
                    + ", height=" + height + ", frameCount=" + frameCount + "]";
        }
    }
}

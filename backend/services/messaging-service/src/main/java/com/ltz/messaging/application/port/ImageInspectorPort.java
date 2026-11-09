package com.ltz.messaging.application.port;

import java.util.Arrays;
import java.util.Objects;

/**
 * Yüklenen görselin sunucu tarafı doğrulaması ve temizliği (MSG-17, SECURITY.md §13). Biçim içerikten (magic byte)
 * belirlenir ve bildirilen {@code Content-Type} ile aynı olmalıdır; boyut ve piksel sınırı çözmeden önce uygulanır.
 * Dönen içerik saklanmaya hazırdır: metadata (EXIF/konum) ve gömülü veri atılmıştır, boyutu en çok 1 MB'tır.
 */
public interface ImageInspectorPort {

    /**
     * @throws com.ltz.messaging.domain.exception.MessagingException {@code INVALID_IMAGE},
     *         {@code IMAGE_DIMENSIONS_EXCEEDED}, {@code IMAGE_TOO_LARGE} ya da {@code MEDIA_BUSY}
     */
    ProcessedImage process(byte[] content, String declaredContentType);

    /** Temizlenmiş görsel ve ölçüleri. {@code frames}: GIF'te kare sayısı, diğerlerinde 1. */
    record ProcessedImage(String mimeType, byte[] content, int width, int height, int frames) {

        @Override
        public boolean equals(Object other) {
            return other instanceof ProcessedImage that && width == that.width && height == that.height
                    && frames == that.frames && Objects.equals(mimeType, that.mimeType)
                    && Arrays.equals(content, that.content);
        }

        @Override
        public int hashCode() {
            return 31 * Objects.hash(mimeType, width, height, frames) + Arrays.hashCode(content);
        }

        @Override
        public String toString() {
            return "ProcessedImage[mimeType=" + mimeType + ", " + width + "x" + height + ", frames=" + frames
                    + ", size=" + content.length + "]";
        }
    }
}

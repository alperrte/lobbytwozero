package com.ltz.messaging.application.port;

import java.util.Arrays;
import java.util.Objects;

/**
 * Yüklenen sesli mesajın sunucu tarafı doğrulaması ve yeniden paketlenmesi (MSG-21, SECURITY.md §13). Yalnız Opus
 * (WebM ya da Ogg) kabul edilir; biçim içerikten belirlenir ve bildirilen {@code Content-Type} ile aynı olmalıdır. Dosya
 * olduğu gibi saklanmaz: yalnız doğrulanmış Opus paketleri sıfırdan yazılan bir WebM'e taşınır. Süre paketlerden
 * hesaplanır; dönen içerik en çok 1 MB'tır.
 */
public interface VoiceInspectorPort {

    /**
     * @throws com.ltz.messaging.domain.exception.MessagingException {@code INVALID_AUDIO},
     *         {@code AUDIO_DURATION_OUT_OF_RANGE} ya da {@code AUDIO_TOO_LARGE}
     */
    ProcessedVoice process(byte[] content, String declaredContentType);

    /** Yeniden paketlenmiş ses ({@code audio/webm}) ve çalınma süresi. */
    record ProcessedVoice(String mimeType, byte[] content, int durationMillis) {

        @Override
        public boolean equals(Object other) {
            return other instanceof ProcessedVoice that && durationMillis == that.durationMillis
                    && Objects.equals(mimeType, that.mimeType) && Arrays.equals(content, that.content);
        }

        @Override
        public int hashCode() {
            return 31 * Objects.hash(mimeType, durationMillis) + Arrays.hashCode(content);
        }

        @Override
        public String toString() {
            return "ProcessedVoice[mimeType=" + mimeType + ", durationMillis=" + durationMillis + ", size="
                    + content.length + "]";
        }
    }
}

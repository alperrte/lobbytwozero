package com.ltz.messaging.infrastructure.storage;

import java.util.Locale;

/** Bildirilen {@code Content-Type}'ın karşılaştırılabilir biçimi. */
final class MediaTypes {

    private MediaTypes() {
    }

    /** {@code Audio/OGG; codecs=opus} → {@code audio/ogg}; {@code null} → boş dizgi. */
    static String normalize(String contentType) {
        if (contentType == null) {
            return "";
        }
        int parameters = contentType.indexOf(';');
        String type = parameters < 0 ? contentType : contentType.substring(0, parameters);
        return type.trim().toLowerCase(Locale.ROOT);
    }
}

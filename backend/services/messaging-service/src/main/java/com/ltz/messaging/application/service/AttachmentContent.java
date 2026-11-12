package com.ltz.messaging.application.service;

import java.util.Arrays;
import java.util.Objects;

/** İndirilecek görsel: saklanan (doğrulanmış) türü ve içeriği. */
public record AttachmentContent(String mimeType, byte[] content) {

    @Override
    public boolean equals(Object other) {
        return other instanceof AttachmentContent that && Objects.equals(mimeType, that.mimeType)
                && Arrays.equals(content, that.content);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hashCode(mimeType) + Arrays.hashCode(content);
    }

    @Override
    public String toString() {
        return "AttachmentContent[mimeType=" + mimeType + ", size=" + content.length + "]";
    }
}

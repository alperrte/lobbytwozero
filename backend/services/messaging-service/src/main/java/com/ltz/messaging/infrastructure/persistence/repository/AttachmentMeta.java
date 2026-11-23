package com.ltz.messaging.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * Ek satırının içerik dışındaki alanları; listeler ve yetki denetimi içeriği belleğe almaz. Görselde ölçüler, seste
 * süre ve dalga formu doludur.
 */
public record AttachmentMeta(UUID id, UUID conversationId, UUID uploaderId, UUID messageId, String mimeType,
                             int sizeBytes, Integer width, Integer height, Integer frameCount, Integer durationMs,
                             byte[] waveform, Instant createdAt) {

    @Override
    public boolean equals(Object other) {
        return other instanceof AttachmentMeta(UUID otherId, UUID otherConversationId, UUID otherUploaderId,
                                               UUID otherMessageId, String otherMimeType, int otherSizeBytes,
                                               Integer otherWidth, Integer otherHeight, Integer otherFrameCount,
                                               Integer otherDurationMs, byte[] otherWaveform, Instant otherCreatedAt)
                && sizeBytes == otherSizeBytes && Objects.equals(id, otherId)
                && Objects.equals(conversationId, otherConversationId) && Objects.equals(uploaderId, otherUploaderId)
                && Objects.equals(messageId, otherMessageId) && Objects.equals(mimeType, otherMimeType)
                && Objects.equals(width, otherWidth) && Objects.equals(height, otherHeight)
                && Objects.equals(frameCount, otherFrameCount) && Objects.equals(durationMs, otherDurationMs)
                && Arrays.equals(waveform, otherWaveform) && Objects.equals(createdAt, otherCreatedAt);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(id, conversationId, uploaderId, messageId, mimeType, sizeBytes, width, height,
                frameCount, durationMs, createdAt) + Arrays.hashCode(waveform);
    }

    @Override
    public String toString() {
        return "AttachmentMeta[id=" + id + ", conversationId=" + conversationId + ", mimeType=" + mimeType
                + ", sizeBytes=" + sizeBytes + ", waveform=" + (waveform == null ? "yok" : waveform.length + " bayt")
                + "]";
    }
}

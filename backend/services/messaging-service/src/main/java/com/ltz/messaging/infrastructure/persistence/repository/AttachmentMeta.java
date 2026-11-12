package com.ltz.messaging.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.UUID;

/**
 * Ek satırının içerik dışındaki alanları; listeler ve yetki denetimi içeriği belleğe almaz. Görselde ölçüler, seste
 * süre ve dalga formu doludur.
 */
public record AttachmentMeta(UUID id, UUID conversationId, UUID uploaderId, UUID messageId, String mimeType,
                             int sizeBytes, Integer width, Integer height, Integer frameCount, Integer durationMs,
                             byte[] waveform, Instant createdAt) {
}

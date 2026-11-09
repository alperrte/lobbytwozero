package com.ltz.messaging.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.UUID;

/** Görsel satırının içerik dışındaki alanları; listeler ve yetki denetimi içeriği belleğe almaz. */
public record AttachmentMeta(UUID id, UUID conversationId, UUID uploaderId, UUID messageId, String mimeType,
                             int sizeBytes, int width, int height, int frameCount, Instant createdAt) {
}

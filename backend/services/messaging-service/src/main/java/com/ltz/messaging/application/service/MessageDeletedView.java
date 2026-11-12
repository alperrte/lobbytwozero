package com.ltz.messaging.application.service;

import java.time.Instant;
import java.util.UUID;

/** {@code message_deleted} olayının yükü (MSG-26): istemci mesajı "Bu mesaj silindi" ile değiştirir. İçerik taşımaz. */
public record MessageDeletedView(UUID conversationId, UUID messageId, long seq, Instant deletedAt) {
}

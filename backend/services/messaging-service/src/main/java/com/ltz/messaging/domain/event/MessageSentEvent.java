package com.ltz.messaging.domain.event;

import java.util.List;
import java.util.UUID;

import com.ltz.messaging.domain.enums.ConversationType;

/**
 * {@code MessageSent} v1 (MSG-16): yeni mesajı notification-service'e bildirir. Yük yalnız kimlik ve tür taşır; mesaj
 * içeriği ve önizleme taşımaz (SECURITY.md §6, mesaj içeriği gizliliğe bağlıdır). {@code recipientIds}, mesaj anında
 * gönderen dışındaki aktif üyelerdir.
 */
public record MessageSentEvent(UUID messageId, UUID conversationId, ConversationType conversationType, long seq,
                               UUID senderId, List<UUID> recipientIds) {

    public static final String EVENT_TYPE = "MessageSent";
    public static final int EVENT_VERSION = 1;
    public static final String AGGREGATE_TYPE = "Conversation";
}

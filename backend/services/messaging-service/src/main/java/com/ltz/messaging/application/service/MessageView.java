package com.ltz.messaging.application.service;

import java.time.Instant;
import java.util.UUID;

import com.ltz.messaging.domain.entity.Message;
import com.ltz.messaging.domain.enums.MessageType;

/** Mesaj ve bilgileri (MSG-10): gönderen, oluşturulma zamanı, sohbet içi sıra. */
public record MessageView(UUID id, UUID conversationId, long seq, UUID senderId, MessageType type, String body,
                          String clientMessageId, Instant createdAt) {

    public static MessageView of(Message message) {
        return new MessageView(message.getId(), message.getConversationId(), message.getSeq(), message.getSenderId(),
                message.getType(), message.getBody(), message.getClientMessageId(), message.getCreatedAt());
    }
}

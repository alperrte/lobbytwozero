package com.ltz.messaging.application.service;

import java.time.Instant;
import java.util.UUID;

import com.ltz.messaging.domain.entity.Message;
import com.ltz.messaging.domain.enums.MessageType;
import com.ltz.messaging.domain.enums.ReadStatus;

/**
 * Mesaj ve bilgileri (MSG-10): gönderen, oluşturulma zamanı, sohbet içi sıra. {@code readStatus} yalnız isteyenin kendi
 * mesajında dolar (MSG-11); aksi halde {@code null}'dır.
 */
public record MessageView(UUID id, UUID conversationId, long seq, UUID senderId, MessageType type, String body,
                          String clientMessageId, Instant createdAt, ReadStatus readStatus) {

    public static MessageView of(Message message) {
        return new MessageView(message.getId(), message.getConversationId(), message.getSeq(), message.getSenderId(),
                message.getType(), message.getBody(), message.getClientMessageId(), message.getCreatedAt(), null);
    }

    public MessageView withReadStatus(ReadStatus status) {
        return new MessageView(id, conversationId, seq, senderId, type, body, clientMessageId, createdAt, status);
    }
}

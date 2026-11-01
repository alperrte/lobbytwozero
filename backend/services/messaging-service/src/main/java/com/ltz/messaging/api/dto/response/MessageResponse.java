package com.ltz.messaging.api.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.ltz.messaging.application.service.MessageView;
import com.ltz.messaging.domain.enums.MessageType;

/** Mesaj ve bilgileri (MSG-10). Akıştaki {@code message} olayının verisiyle aynı alanlara sahiptir. */
public record MessageResponse(UUID id, UUID conversationId, long seq, UUID senderId, MessageType type, String body,
                              String clientMessageId, Instant createdAt) {

    public static MessageResponse from(MessageView view) {
        return new MessageResponse(view.id(), view.conversationId(), view.seq(), view.senderId(), view.type(),
                view.body(), view.clientMessageId(), view.createdAt());
    }
}

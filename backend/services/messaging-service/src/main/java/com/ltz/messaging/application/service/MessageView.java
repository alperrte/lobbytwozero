package com.ltz.messaging.application.service;

import java.time.Instant;
import java.util.UUID;

import com.ltz.messaging.domain.entity.Message;
import com.ltz.messaging.domain.enums.MessageType;
import com.ltz.messaging.domain.enums.ReadStatus;

/**
 * Mesaj ve bilgileri (MSG-10): gönderen, oluşturulma zamanı, sohbet içi sıra. {@code readStatus} yalnız isteyenin kendi
 * mesajında dolar (MSG-11); aksi halde {@code null}'dır. {@code attachment} yalnız {@code IMAGE}, {@code gif} yalnız
 * {@code GIF} türünde doludur (MSG-17/22); görsel/GIF mesajında {@code body} isteğe bağlı açıklamadır.
 */
public record MessageView(UUID id, UUID conversationId, long seq, UUID senderId, MessageType type, String body,
                          String clientMessageId, Instant createdAt, ReadStatus readStatus, AttachmentView attachment,
                          GifView gif) {

    /** Görsel ayrı tablodadır; {@code IMAGE} mesajında {@link #withAttachment} ile eklenir. */
    public static MessageView of(Message message) {
        return new MessageView(message.getId(), message.getConversationId(), message.getSeq(), message.getSenderId(),
                message.getType(), message.getBody(), message.getClientMessageId(), message.getCreatedAt(), null, null,
                GifView.of(message.getGif()));
    }

    public MessageView withReadStatus(ReadStatus status) {
        return new MessageView(id, conversationId, seq, senderId, type, body, clientMessageId, createdAt, status,
                attachment, gif);
    }

    public MessageView withAttachment(AttachmentView view) {
        return new MessageView(id, conversationId, seq, senderId, type, body, clientMessageId, createdAt, readStatus,
                view, gif);
    }
}

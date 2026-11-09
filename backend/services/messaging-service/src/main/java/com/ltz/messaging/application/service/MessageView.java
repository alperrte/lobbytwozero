package com.ltz.messaging.application.service;

import java.time.Instant;
import java.util.UUID;

import com.ltz.messaging.domain.entity.Message;
import com.ltz.messaging.domain.enums.MessageType;
import com.ltz.messaging.domain.enums.ReadStatus;

/**
 * Mesajın okuma modeli (geçmiş, sohbet listesi, gönderim yanıtı, SSE). İzleyene göre değişen alanlar ({@code readStatus},
 * {@code replyTo}, {@code reactions.mine}, {@code deletableForEveryoneUntil}) {@link MessageViews} ile eklenir.
 * {@code replyToMessageId} yalnız iç kullanım içindir; dışarıya {@code replyTo} önizlemesi çıkar.
 */
public record MessageView(UUID id, UUID conversationId, long seq, UUID senderId, MessageType type, String body,
                          String clientMessageId, Instant createdAt, ReadStatus readStatus, AttachmentView attachment,
                          GifView gif, UUID replyToMessageId, ReplyPreview replyTo, int forwardCount, boolean deleted,
                          ReactionsView reactions, Instant deletableForEveryoneUntil) {

    public static MessageView of(Message message) {
        return new MessageView(message.getId(), message.getConversationId(), message.getSeq(), message.getSenderId(),
                message.getType(), message.getBody(), message.getClientMessageId(), message.getCreatedAt(), null, null,
                GifView.of(message.getGif()), message.getReplyToMessageId(), null, message.getForwardCount(),
                message.isDeleted(), ReactionsView.EMPTY, null);
    }

    public MessageView withReadStatus(ReadStatus status) {
        return new MessageView(id, conversationId, seq, senderId, type, body, clientMessageId, createdAt, status,
                attachment, gif, replyToMessageId, replyTo, forwardCount, deleted, reactions,
                deletableForEveryoneUntil);
    }

    public MessageView withAttachment(AttachmentView view) {
        return new MessageView(id, conversationId, seq, senderId, type, body, clientMessageId, createdAt, readStatus,
                view, gif, replyToMessageId, replyTo, forwardCount, deleted, reactions, deletableForEveryoneUntil);
    }

    public MessageView withReplyTo(ReplyPreview preview) {
        return new MessageView(id, conversationId, seq, senderId, type, body, clientMessageId, createdAt, readStatus,
                attachment, gif, replyToMessageId, preview, forwardCount, deleted, reactions,
                deletableForEveryoneUntil);
    }

    public MessageView withReactions(ReactionsView view) {
        return new MessageView(id, conversationId, seq, senderId, type, body, clientMessageId, createdAt, readStatus,
                attachment, gif, replyToMessageId, replyTo, forwardCount, deleted, view, deletableForEveryoneUntil);
    }

    public MessageView withDeletableUntil(Instant until) {
        return new MessageView(id, conversationId, seq, senderId, type, body, clientMessageId, createdAt, readStatus,
                attachment, gif, replyToMessageId, replyTo, forwardCount, deleted, reactions, until);
    }
}

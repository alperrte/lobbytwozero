package com.ltz.messaging.application.service;

import java.util.UUID;

import com.ltz.messaging.domain.enums.GifProvider;
import com.ltz.messaging.domain.enums.MessageType;
import com.ltz.messaging.domain.exception.MessagingErrorCode;
import com.ltz.messaging.domain.exception.MessagingException;

/**
 * Gönderilmek istenen mesajın içeriği: metin, görsel ({@code attachmentId}) ya da GIF ({@code gifProvider} +
 * {@code gifId}); görsel ve GIF'te {@code body} isteğe bağlı açıklamadır. Görsel ile GIF birlikte olamaz.
 * {@code replyToMessageId} isteğe bağlı yanıt hedefidir (MSG-19).
 */
public record MessageDraft(String body, UUID attachmentId, GifProvider gifProvider, String gifId,
                           UUID replyToMessageId) {

    public MessageDraft {
        if (attachmentId != null && gifId != null) {
            throw new MessagingException(MessagingErrorCode.CONFLICTING_MESSAGE_CONTENT);
        }
        if (attachmentId == null && gifId == null && body == null) {
            throw new MessagingException(MessagingErrorCode.MESSAGE_CONTENT_REQUIRED);
        }
        if ((gifId == null) != (gifProvider == null)) {
            throw new MessagingException(MessagingErrorCode.MESSAGE_CONTENT_REQUIRED);
        }
    }

    public static MessageDraft text(String body) {
        return new MessageDraft(body, null, null, null, null);
    }

    public MessageType type() {
        if (attachmentId != null) {
            return MessageType.IMAGE;
        }
        return gifId != null ? MessageType.GIF : MessageType.TEXT;
    }
}

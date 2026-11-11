package com.ltz.messaging.application.service;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ltz.messaging.domain.entity.ConversationMember;
import com.ltz.messaging.domain.entity.HiddenMessageId;
import com.ltz.messaging.domain.entity.Message;
import com.ltz.messaging.domain.exception.MessagingErrorCode;
import com.ltz.messaging.domain.exception.MessagingException;
import com.ltz.messaging.infrastructure.persistence.repository.HiddenMessageRepository;
import com.ltz.messaging.infrastructure.persistence.repository.MessageRepository;

/**
 * Mesaja yapılan her işlemin (yanıt, iletme, tepki, silme) ortak ön koşulu: mesaj üyenin sohbetinde, görünürlük sınırının
 * ({@code visible_from_seq}) içinde ve üye onu kendi görünümünden kaldırmamış olmalı (MSG-25). Aksi her durum aynı
 * {@code 404 MESSAGE_NOT_FOUND}'dur; başka sohbetin ya da görülemeyen mesajın varlığı sızdırılmaz. Çağıranın
 * transaction'ına katılır.
 */
@Component
public class VisibleMessages {

    private final MessageRepository messages;
    private final HiddenMessageRepository hidden;

    public VisibleMessages(MessageRepository messages, HiddenMessageRepository hidden) {
        this.messages = messages;
        this.hidden = hidden;
    }

    public Message require(ConversationMember member, UUID messageId) {
        return check(member, messages.findByIdAndConversationId(messageId, member.getId().getConversationId())
                .orElse(null));
    }

    /** Herkesten silinmiş mesajda işlem yapılamaz (yanıt, iletme, tepki): {@code 422 MESSAGE_DELETED}. */
    public static Message requireNotDeleted(Message message) {
        if (message.isDeleted()) {
            throw new MessagingException(MessagingErrorCode.MESSAGE_DELETED);
        }
        return message;
    }

    /** Kilitli okunmuş (ya da başka yoldan bulunmuş) mesajı aynı kurala göre denetler. */
    public Message check(ConversationMember member, Message message) {
        if (message == null || !message.getConversationId().equals(member.getId().getConversationId())
                || !member.canSee(message.getSeq())
                || hidden.existsById(new HiddenMessageId(member.getUserId(), message.getId()))) {
            throw new MessagingException(MessagingErrorCode.MESSAGE_NOT_FOUND);
        }
        return message;
    }
}

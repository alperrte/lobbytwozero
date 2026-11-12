package com.ltz.messaging.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.messaging.application.port.MessageStreamPort;
import com.ltz.messaging.domain.entity.ConversationMember;
import com.ltz.messaging.domain.entity.Message;
import com.ltz.messaging.domain.exception.MessagingErrorCode;
import com.ltz.messaging.domain.exception.MessagingException;
import com.ltz.messaging.infrastructure.config.MessageProperties;
import com.ltz.messaging.infrastructure.persistence.repository.AttachmentBlobRepository;
import com.ltz.messaging.infrastructure.persistence.repository.ConversationMemberRepository;
import com.ltz.messaging.infrastructure.persistence.repository.HiddenMessageRepository;
import com.ltz.messaging.infrastructure.persistence.repository.MessageAttachmentRepository;
import com.ltz.messaging.infrastructure.persistence.repository.MessageReactionRepository;
import com.ltz.messaging.infrastructure.persistence.repository.MessageRepository;

import lombok.RequiredArgsConstructor;

/**
 * MSG-26 herkesten sil. Yetki: mesajın göndereni ya da grupta OWNER/ADMIN; süre: gönderimden sonraki
 * {@code deleteForEveryoneWindow} (varsayılan 48 saat). Tek transaction'da, mesaj satırı kilitliyken: gövde ve GIF kaydı
 * temizlenir, görsel bağlantısı silinir ve içeriğe başka bağlantı kalmadıysa içerik de silinir (iletilmiş kopyalar
 * etkilenmez), tepkiler silinir. Satır kalır: sıra, okunmamış sayısı ve okundu bilgisi bozulmaz. İdempotenttir. Commit
 * sonrası mesajı görebilen ve gizlememiş aktif üyelere {@code message_deleted} gider.
 */
@Service
@RequiredArgsConstructor
public class MessageDeletionService {

    private final ConversationAccessPolicy access;
    private final VisibleMessages visible;
    private final MessageRepository messages;
    private final MessageAttachmentRepository attachments;
    private final AttachmentBlobRepository blobs;
    private final MessageReactionRepository reactions;
    private final HiddenMessageRepository hidden;
    private final ConversationMemberRepository members;
    private final MessageProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional
    public void deleteForEveryone(UUID actor, UUID conversationId, UUID messageId) {
        ConversationMember member = access.requireMember(conversationId, actor);
        Message message = visible.check(member,
                messages.findByIdAndConversationIdForUpdate(messageId, conversationId).orElse(null));
        if (message.isDeleted()) {
            return;
        }
        if (!MessageViews.canDeleteForEveryone(message.getSenderId(), member)) {
            throw new MessagingException(MessagingErrorCode.NOT_ALLOWED_TO_DELETE);
        }
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        if (!message.getCreatedAt().plus(properties.deleteForEveryoneWindow()).isAfter(now)) {
            throw new MessagingException(MessagingErrorCode.DELETE_WINDOW_EXPIRED);
        }
        message.deleteForEveryone(actor, now);
        messages.flush();
        attachments.findBlobIdByMessageId(message.getId()).ifPresent(blobId -> {
            attachments.deleteByMessageId(message.getId());
            blobs.deleteIfOrphan(blobId);
        });
        reactions.deleteByMessageId(message.getId());

        Set<UUID> hid = hidden.findUsersWhoHid(message.getId());
        MessageDeletedView payload = new MessageDeletedView(conversationId, message.getId(), message.getSeq(), now);
        Map<UUID, Object> recipients = new LinkedHashMap<>();
        members.findActiveMembers(conversationId).stream()
                .filter(recipient -> recipient.canSee(message.getSeq()) && !hid.contains(recipient.getUserId()))
                .forEach(recipient -> recipients.put(recipient.getUserId(), payload));
        events.publishEvent(new MessageChanged(MessageStreamPort.DELETED_EVENT, recipients));
    }
}

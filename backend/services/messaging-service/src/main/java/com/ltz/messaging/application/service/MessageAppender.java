package com.ltz.messaging.application.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongFunction;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.ltz.messaging.domain.entity.Conversation;
import com.ltz.messaging.domain.entity.ConversationMember;
import com.ltz.messaging.domain.entity.Message;
import com.ltz.messaging.domain.enums.ReadStatus;
import com.ltz.messaging.domain.event.MessageSentEvent;
import com.ltz.messaging.domain.exception.MessagingErrorCode;
import com.ltz.messaging.domain.exception.MessagingException;
import com.ltz.messaging.infrastructure.config.MessageProperties;
import com.ltz.messaging.infrastructure.persistence.repository.ConversationMemberRepository;
import com.ltz.messaging.infrastructure.persistence.repository.MessageRepository;

import lombok.RequiredArgsConstructor;

/**
 * Yeni mesajın ortak ekleme adımları (gönderim MSG-06 ve iletme MSG-20): sohbet satırı kilitliyken {@code seq} alınır,
 * mesaj kaydedilir, gönderenin okuma işaretçisi ilerler, {@code MessageSent} olayı aynı transaction'da outbox'a yazılır
 * ve commit sonrası akışlara alıcı başına yükle iletilir. Çağıranın transaction'ına katılır.
 */
@Component
@RequiredArgsConstructor
public class MessageAppender {

    private static final Duration RATE_WINDOW = Duration.ofMinutes(1);

    private final MessageRepository messages;
    private final ConversationMemberRepository members;
    private final ReadTracker tracker;
    private final OutboxRecorder outbox;
    private final MessageViews views;
    private final ApplicationEventPublisher events;
    private final MessageProperties properties;

    /** Gönderim oranı (SECURITY.md §12): son 60 sn'deki mesajlar ve bu istekte eklenecek {@code count} mesaj. */
    public void requireWithinRate(UUID sender, int count, Instant now) {
        if (messages.countBySenderIdAndCreatedAtAfter(sender, now.minus(RATE_WINDOW)) + count
                > properties.maxPerMinute()) {
            throw new MessagingException(MessagingErrorCode.TOO_MANY_MESSAGES);
        }
    }

    /**
     * {@code conversation} satırı kilitli olmalıdır. {@code afterSave} mesaj kaydedildikten sonra, yayından önce çalışır
     * (ör. görsel bağlantısı); hata fırlatırsa transaction ile birlikte mesaj ve sıra da geri alınır.
     *
     * @return gönderenin yükü, {@code SENT} durumuyla
     */
    public MessageView append(Conversation conversation, ConversationMember sender, Instant now,
                              LongFunction<Message> factory, Consumer<Message> afterSave) {
        long seq = conversation.appendMessage(now);
        Message message = messages.saveAndFlush(factory.apply(seq));
        afterSave.accept(message);
        tracker.advance(sender, seq, now);
        UUID senderId = sender.getUserId();
        List<ConversationMember> recipients = members.findActiveMembers(conversation.getId());
        Map<UUID, MessageView> payloads = views.forRecipients(MessageView.of(message), recipients);
        outbox.messageSent(new MessageSentEvent(message.getId(), conversation.getId(), conversation.getType(), seq,
                senderId, recipients.stream().map(ConversationMember::getUserId)
                        .filter(recipient -> !recipient.equals(senderId)).toList()), now);
        events.publishEvent(new MessageCreated(payloads));
        // Yeni mesajı henüz kimse okumadı; akış olayı durumsuz kalır (gönderene hub SENT ekler).
        return payloads.get(senderId).withReadStatus(ReadStatus.SENT);
    }
}

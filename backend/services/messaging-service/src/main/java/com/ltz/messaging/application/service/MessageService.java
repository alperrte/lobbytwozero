package com.ltz.messaging.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.ltz.messaging.domain.entity.Conversation;
import com.ltz.messaging.domain.entity.ConversationMember;
import com.ltz.messaging.domain.entity.Message;
import com.ltz.messaging.domain.event.MessageSentEvent;
import com.ltz.messaging.domain.exception.MessagingErrorCode;
import com.ltz.messaging.domain.exception.MessagingException;
import com.ltz.messaging.infrastructure.config.MessageProperties;
import com.ltz.messaging.infrastructure.persistence.repository.ConversationMemberRepository;
import com.ltz.messaging.infrastructure.persistence.repository.ConversationRepository;
import com.ltz.messaging.infrastructure.persistence.repository.MessageRepository;

import lombok.RequiredArgsConstructor;

/**
 * Metin mesajı gönderme (MSG-06), mesaj geçmişi (MSG-07, MSG-10) ve okuma işaretçisi (MSG-13).
 * <p>
 * Gönderim sırası: aynı {@code clientMessageId} ile daha önce kaydedilmiş mesaj varsa o döner (yeniden deneme, yan etki
 * yok) → yazma yetkisi (aktif üye; birebirde engel yok, MSG-14/15; engel sorgusu transaction dışında) → sohbet satırı
 * kilitlenir, üyelik ve oran sınırı yeniden denetlenir, mesaj {@code seq} alır, gönderenin okuma işaretçisi ilerler ve
 * {@code MessageSent} olayı aynı transaction'da outbox'a yazılır → commit sonrası açık akışlara iletilir (MSG-09).
 */
@Service
@RequiredArgsConstructor
public class MessageService {

    private static final Duration RATE_WINDOW = Duration.ofMinutes(1);

    private final ConversationRepository conversations;
    private final ConversationMemberRepository members;
    private final MessageRepository messages;
    private final ConversationAccessPolicy access;
    private final OutboxRecorder outbox;
    private final ApplicationEventPublisher events;
    private final MessageProperties properties;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public SendMessageResult send(UUID sender, UUID conversationId, String clientMessageId, String body) {
        Optional<MessageView> replay = Objects.requireNonNull(
                transactions.execute(status -> findRetry(sender, conversationId, clientMessageId, body)));
        if (replay.isPresent()) {
            return new SendMessageResult(replay.get(), false);
        }
        access.requireCanWrite(conversationId, sender);
        try {
            return Objects.requireNonNull(
                    transactions.execute(status -> insert(sender, conversationId, clientMessageId, body)));
        } catch (DataIntegrityViolationException exception) {
            // Aynı clientMessageId başka bir sohbette eşzamanlı kullanıldı (tekil indeks kazananı seçti).
            throw new MessagingException(MessagingErrorCode.CLIENT_MESSAGE_ID_REUSED, exception);
        }
    }

    /**
     * MSG-07. {@code after} verilirse ondan sonraki mesajlar eskiden yeniye (yeniden bağlanınca eşitleme); aksi halde
     * {@code before}'dan (yoksa en sondan) önceki mesajlar yeniden eskiye. Yalnız aktif üye görür ve yalnız katıldıktan
     * sonraki mesajları görür.
     */
    @Transactional(readOnly = true)
    public MessagePageView history(UUID viewer, UUID conversationId, Long before, Long after, Integer limit) {
        if (before != null && after != null) {
            throw new MessagingException(MessagingErrorCode.CONFLICTING_PAGE_DIRECTION);
        }
        ConversationMember member = access.requireMember(conversationId, viewer);
        int size = PageSizes.resolve(limit, properties.defaultPageSize(), properties.maxPageSize());
        Limit fetch = Limit.of(size + 1);
        long fromSeq = member.getVisibleFromSeq();
        List<Message> rows;
        if (after != null) {
            rows = messages.findNewer(conversationId, after, fromSeq, fetch);
        } else {
            long upperBound = before == null ? Long.MAX_VALUE : before;
            rows = messages.findOlder(conversationId, upperBound, fromSeq, fetch);
        }
        return new MessagePageView(rows.stream().limit(size).map(MessageView::of).toList(), rows.size() > size);
    }

    /**
     * MSG-13. Okuma işaretçisini {@code seq}'e kadar ilerletir (geri gitmez; sohbetteki son mesajın ötesine geçmez) ve
     * kalan okunmamış sayısını döner.
     */
    @Transactional
    public ReadStateView markRead(UUID reader, UUID conversationId, long seq) {
        ConversationMember member = access.requireMember(conversationId, reader);
        long lastMessage = conversations.findById(conversationId)
                .orElseThrow(() -> new MessagingException(MessagingErrorCode.CONVERSATION_NOT_FOUND))
                .getLastMessageSeq();
        long target = Math.min(seq, lastMessage);
        long lastRead = Math.max(member.getLastReadSeq(), target);
        members.advanceLastRead(conversationId, reader, target);
        long unread = members.countUnread(reader, List.of(conversationId)).stream()
                .mapToLong(row -> (Long) row[1])
                .sum();
        return new ReadStateView(conversationId, lastRead, unread);
    }

    private Optional<MessageView> findRetry(UUID sender, UUID conversationId, String clientMessageId, String body) {
        access.requireMember(conversationId, sender);
        return messages.findBySenderIdAndClientMessageId(sender, clientMessageId)
                .map(existing -> {
                    if (!existing.isRetryOf(conversationId, body)) {
                        throw new MessagingException(MessagingErrorCode.CLIENT_MESSAGE_ID_REUSED);
                    }
                    return MessageView.of(existing);
                });
    }

    private SendMessageResult insert(UUID sender, UUID conversationId, String clientMessageId, String body) {
        Conversation conversation = conversations.findByIdForUpdate(conversationId)
                .orElseThrow(() -> new MessagingException(MessagingErrorCode.CONVERSATION_NOT_FOUND));
        ConversationMember senderMember = access.requireMember(conversationId, sender);
        // Kilit beklenirken aynı istek (aynı sohbette) kaydedilmiş olabilir.
        Optional<MessageView> retried = findRetry(sender, conversationId, clientMessageId, body);
        if (retried.isPresent()) {
            return new SendMessageResult(retried.get(), false);
        }
        Instant now = now();
        if (messages.countBySenderIdAndCreatedAtAfter(sender, now.minus(RATE_WINDOW)) >= properties.maxPerMinute()) {
            throw new MessagingException(MessagingErrorCode.TOO_MANY_MESSAGES);
        }
        long seq = conversation.appendMessage(now);
        Message message = messages.saveAndFlush(Message.text(conversationId, seq, sender, clientMessageId, body, now));
        senderMember.markRead(seq);
        List<UUID> recipients = members.findActiveMembers(conversationId).stream()
                .map(ConversationMember::getUserId)
                .toList();
        MessageView view = MessageView.of(message);
        outbox.messageSent(new MessageSentEvent(message.getId(), conversationId, conversation.getType(), seq, sender,
                recipients.stream().filter(recipient -> !recipient.equals(sender)).toList()), now);
        events.publishEvent(new MessageCreated(view, recipients));
        return new SendMessageResult(view, true);
    }

    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}

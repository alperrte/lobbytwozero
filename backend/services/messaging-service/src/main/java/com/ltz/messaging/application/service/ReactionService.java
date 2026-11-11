package com.ltz.messaging.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.messaging.application.port.MessageStreamPort;
import com.ltz.messaging.domain.entity.ConversationMember;
import com.ltz.messaging.domain.entity.Message;
import com.ltz.messaging.domain.entity.MessageReaction;
import com.ltz.messaging.domain.exception.MessagingErrorCode;
import com.ltz.messaging.domain.exception.MessagingException;
import com.ltz.messaging.infrastructure.persistence.repository.ConversationMemberRepository;
import com.ltz.messaging.infrastructure.persistence.repository.HiddenMessageRepository;
import com.ltz.messaging.infrastructure.persistence.repository.MessageReactionRepository;
import com.ltz.messaging.infrastructure.persistence.repository.MessageRepository;

import lombok.RequiredArgsConstructor;

/**
 * MSG-23 mesaj tepkileri. Kişi başına mesaj başına tek tepki: yeni tepki eskisinin yerine yazılır (upsert), kaldırma
 * idempotenttir. Mesaj görülebilmeli (görünürlük sınırı, benden sil) ve herkesten silinmemiş olmalı; mesaj satırı
 * paylaşımlı kilitle okunur, eşzamanlı herkesten silme tepkiyi bekler ve silinmiş mesajda tepki satırı kalmaz.
 * Değişiklik commit sonrası mesajı görebilen ve gizlememiş aktif üyelere {@code reaction} olayıyla, alıcıya göre
 * {@code mine} ile gider. Tepki listesini mesajı görebilen her üye görür.
 */
@Service
@RequiredArgsConstructor
public class ReactionService {

    private static final Comparator<ReactionCount> SUMMARY_ORDER =
            Comparator.comparingLong(ReactionCount::count).reversed();

    private final ConversationAccessPolicy access;
    private final VisibleMessages visible;
    private final MessageRepository messages;
    private final MessageReactionRepository reactions;
    private final HiddenMessageRepository hidden;
    private final ConversationMemberRepository members;
    private final ReactionRateLimiter limiter;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional
    public ReactionsView react(UUID user, UUID conversationId, UUID messageId, String emoji) {
        String valid = ReactionEmoji.validate(emoji);
        if (!limiter.tryAcquire(user)) {
            throw new MessagingException(MessagingErrorCode.TOO_MANY_REACTIONS);
        }
        Message message = lockLive(user, conversationId, messageId);
        reactions.upsert(message.getId(), user, valid, Instant.now(clock).truncatedTo(ChronoUnit.MICROS));
        return new ReactionsView(publish(message), valid);
    }

    @Transactional
    public void unreact(UUID user, UUID conversationId, UUID messageId) {
        Message message = lockLive(user, conversationId, messageId);
        if (reactions.deleteByMessageIdAndUserId(message.getId(), user) > 0) {
            publish(message);
        }
    }

    @Transactional(readOnly = true)
    public MessageReactionsView list(UUID viewer, UUID conversationId, UUID messageId) {
        ConversationMember member = access.requireMember(conversationId, viewer);
        Message message = visible.require(member, messageId);
        List<MessageReactionsView.Entry> entries = message.isDeleted() ? List.of()
                : reactions.findNewestFirst(message.getId()).stream()
                        .map(reaction -> new MessageReactionsView.Entry(reaction.getId().getUserId(),
                                reaction.getEmoji(), reaction.getReactedAt()))
                        .toList();
        return new MessageReactionsView(message.getId(), entries);
    }

    private Message lockLive(UUID user, UUID conversationId, UUID messageId) {
        ConversationMember member = access.requireMember(conversationId, user);
        return VisibleMessages.requireNotDeleted(visible.check(member,
                messages.findByIdAndConversationIdForShare(messageId, conversationId).orElse(null)));
    }

    /** Güncel özeti hesaplar, commit sonrası iletilmek üzere olay yayınlar ve özeti döner. */
    private List<ReactionCount> publish(Message message) {
        List<MessageReaction> rows = reactions.findNewestFirst(message.getId());
        List<ReactionCount> summary = summarize(rows);
        Map<UUID, String> byUser = rows.stream()
                .collect(Collectors.toMap(reaction -> reaction.getId().getUserId(), MessageReaction::getEmoji));
        Set<UUID> hid = hidden.findUsersWhoHid(message.getId());
        Map<UUID, Object> payloads = new LinkedHashMap<>();
        members.findActiveMembers(message.getConversationId()).stream()
                .filter(recipient -> recipient.canSee(message.getSeq()) && !hid.contains(recipient.getUserId()))
                .forEach(recipient -> payloads.put(recipient.getUserId(), new ReactionEventView(
                        message.getConversationId(), message.getId(), summary, byUser.get(recipient.getUserId()))));
        events.publishEvent(new MessageChanged(MessageStreamPort.REACTION_EVENT, payloads));
        return summary;
    }

    /** Emoji başına sayı, çoktan aza; eşitlikte ilk verilen tepki önce ({@link MessageViews} ile aynı sıra). */
    private static List<ReactionCount> summarize(List<MessageReaction> newestFirst) {
        Map<String, Long> counts = new LinkedHashMap<>();
        newestFirst.reversed().forEach(reaction -> counts.merge(reaction.getEmoji(), 1L, Long::sum));
        return counts.entrySet().stream()
                .map(entry -> new ReactionCount(entry.getKey(), entry.getValue()))
                .sorted(SUMMARY_ORDER)
                .toList();
    }
}

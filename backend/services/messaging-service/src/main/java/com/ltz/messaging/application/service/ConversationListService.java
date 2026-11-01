package com.ltz.messaging.application.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.messaging.domain.entity.Conversation;
import com.ltz.messaging.domain.entity.ConversationMember;
import com.ltz.messaging.domain.entity.Message;
import com.ltz.messaging.infrastructure.config.MessageProperties;
import com.ltz.messaging.infrastructure.persistence.repository.ConversationMemberRepository;
import com.ltz.messaging.infrastructure.persistence.repository.ConversationRepository;
import com.ltz.messaging.infrastructure.persistence.repository.MessageRepository;

import lombok.RequiredArgsConstructor;

/**
 * Sohbet listesi (MSG-08) ve sohbet başına okunmamış sayısı (MSG-13). Yalnız kullanıcının aktif üyesi olduğu sohbetler,
 * son etkinlikten eskiye, keyset sayfalamayla listelenir. Bir sayfa sabit sayıda sorguyla kurulur (N+1 yok): sohbetler,
 * üyelikler, son mesajlar, okunmamış sayıları, üye sayıları ve birebir karşı taraflar.
 */
@Service
@RequiredArgsConstructor
public class ConversationListService {

    private final ConversationRepository conversations;
    private final ConversationMemberRepository members;
    private final MessageRepository messages;
    private final MessageProperties properties;

    @Transactional(readOnly = true)
    public ConversationPageView list(UUID userId, Integer limit, String cursor) {
        int size = PageSizes.resolve(limit, properties.defaultConversationPageSize(),
                properties.maxConversationPageSize());
        Limit fetch = Limit.of(size + 1);
        List<Conversation> rows;
        if (cursor == null) {
            rows = conversations.findFirstPageOf(userId, fetch);
        } else {
            ActivityCursor after = ActivityCursor.decode(cursor);
            rows = conversations.findPageOfAfter(userId, after.activityAt(), after.id(), fetch);
        }
        List<Conversation> page = rows.stream().limit(size).toList();
        if (page.isEmpty()) {
            return new ConversationPageView(List.of(), null);
        }
        List<UUID> ids = page.stream().map(Conversation::getId).toList();
        Map<UUID, ConversationMember> memberships = members.findActiveOf(userId, ids).stream()
                .collect(Collectors.toMap(member -> member.getId().getConversationId(), Function.identity()));
        Map<UUID, Message> lastMessages = messages.findLastMessages(ids).stream()
                .collect(Collectors.toMap(Message::getConversationId, Function.identity()));
        Map<UUID, Long> unread = counts(members.countUnread(userId, ids));
        Map<UUID, Long> memberCounts = counts(members.countActive(ids));
        List<UUID> directIds = page.stream().filter(conversation -> !conversation.isGroup())
                .map(Conversation::getId).toList();
        Map<UUID, UUID> peers = directIds.isEmpty() ? Map.of() : members.findActiveOthers(directIds, userId).stream()
                .collect(Collectors.toMap(member -> member.getId().getConversationId(), ConversationMember::getUserId,
                        (first, second) -> first));

        // Sayfa sorgusu ile üyelik sorgusu arasında ayrılan üyeliğin satırı atlanır.
        List<ConversationSummaryView> items = page.stream()
                .filter(conversation -> memberships.containsKey(conversation.getId()))
                .map(conversation -> {
            ConversationMember membership = memberships.get(conversation.getId());
            Message last = lastMessages.get(conversation.getId());
            MessageView visibleLast = last != null && membership.canSee(last.getSeq()) ? MessageView.of(last) : null;
            return new ConversationSummaryView(conversation.getId(), conversation.getType(), conversation.getTitle(),
                    peers.get(conversation.getId()), memberCounts.getOrDefault(conversation.getId(), 0L),
                    membership.getRole(), conversation.getLastActivityAt(), visibleLast,
                    unread.getOrDefault(conversation.getId(), 0L));
        }).toList();
        String nextCursor = rows.size() > size
                ? new ActivityCursor(page.getLast().getLastActivityAt(), page.getLast().getId()).encode()
                : null;
        return new ConversationPageView(items, nextCursor);
    }

    private static Map<UUID, Long> counts(List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));
    }
}

package com.ltz.messaging.application.service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.ltz.messaging.domain.entity.Conversation;
import com.ltz.messaging.domain.entity.ConversationMember;
import com.ltz.messaging.domain.exception.MessagingErrorCode;
import com.ltz.messaging.domain.exception.MessagingException;
import com.ltz.messaging.infrastructure.persistence.repository.ConversationMemberRepository;
import com.ltz.messaging.infrastructure.persistence.repository.ConversationRepository;

import lombok.RequiredArgsConstructor;

/**
 * MSG-14 sohbet erişim kuralı; sohbete dokunan her uç (ve MSG-02'deki mesaj uçları) bu sınıfı kullanır.
 * <ul>
 *   <li><b>Okuma:</b> yalnız sohbetin aktif üyesi. Hiç üye olmamış, ayrılmış ya da çıkarılmış kullanıcı ile var
 *       olmayan sohbet aynı {@code 404 CONVERSATION_NOT_FOUND} alır; sohbetin varlığı sızdırılmaz.</li>
 *   <li><b>Yazma:</b> okuma kuralı + birebir sohbette iki taraf arasında engel olmamalı (MSG-15). Grup sohbetinde
 *       engel üyeliği etkilemez; engel, gruba eklemede uygulanır.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ConversationAccessPolicy {

    private final ConversationRepository conversations;
    private final ConversationMemberRepository members;
    private final BlockGuard blockGuard;
    private final TransactionTemplate transactions;

    /** Çağıranın transaction'ına katılır (yoksa açık transaction gerekmez; tek sorgudur). */
    public ConversationMember requireMember(UUID conversationId, UUID userId) {
        return members.findActive(conversationId, userId)
                .orElseThrow(() -> new MessagingException(MessagingErrorCode.CONVERSATION_NOT_FOUND));
    }

    /**
     * Kullanıcı bu sohbete yazabilir mi? Engel sorgusu ağ çağrısıdır; bu yüzden transaction dışında çağrılmalıdır.
     *
     * @throws MessagingException 404 üye değilse, 403 birebir sohbette engel varsa, 503 engel öğrenilemezse
     */
    public void requireCanWrite(UUID conversationId, UUID userId) {
        List<UUID> counterparts = Objects.requireNonNull(
                transactions.execute(status -> directCounterparts(conversationId, userId)));
        if (!counterparts.isEmpty()) {
            blockGuard.requireNoBlock(userId, counterparts);
        }
    }

    /** Birebir sohbette karşı taraf; grupta boş liste. */
    private List<UUID> directCounterparts(UUID conversationId, UUID userId) {
        requireMember(conversationId, userId);
        Conversation conversation = conversations.findById(conversationId)
                .orElseThrow(() -> new MessagingException(MessagingErrorCode.CONVERSATION_NOT_FOUND));
        if (conversation.isGroup()) {
            return List.of();
        }
        return members.findActiveMembers(conversationId).stream()
                .map(ConversationMember::getUserId)
                .filter(member -> !member.equals(userId))
                .toList();
    }
}

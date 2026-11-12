package com.ltz.messaging.application.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.messaging.domain.entity.ConversationMember;
import com.ltz.messaging.infrastructure.persistence.repository.ConversationMemberRepository;
import com.ltz.messaging.infrastructure.persistence.repository.MessageRepository;

import lombok.RequiredArgsConstructor;

/**
 * Canlı okundu bilgisi (MSG-11, spec §5). Okuma işaretçisi ilerleyince, okunan aralıkta mesajı olan aktif göndericilere
 * (okuyucu hariç) alıcı başına kırpılmış bir yük hazırlanır ve commit sonrası iletilmek üzere olay yayınlanır. Aralıkta
 * mesajı olmayan üye olay almaz; böylece kimse başkasının mesajının okunduğunu öğrenemez. Üye ayrılınca ya da
 * çıkarılınca su seviyesi yükselen göndericiler de aynı olayla haberdar edilir.
 */
@Service
@RequiredArgsConstructor
public class ReadReceiptService {

    private final MessageRepository messages;
    private final ConversationMemberRepository members;
    private final ApplicationEventPublisher events;

    /**
     * Aralığın alt sınırı okuyucunun görebildiği ilk mesaja çekilir: yeni katılanın ilk aralığı 1'den başlar, ama
     * katılmadan önceki mesajların uygun okuyucusu değildir (spec §3.1). Çağıranın transaction'ına katılır.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void onReadAdvanced(ReadAdvance advance) {
        UUID conversationId = advance.conversationId();
        long fromExclusive = Math.max(advance.fromSeq(), advance.visibleFromSeq()) - 1;
        List<Object[]> senders = messages.findOwnMaxSeqInRange(conversationId, fromExclusive, advance.toSeq(),
                advance.readerId());
        if (senders.isEmpty()) {
            return;
        }
        List<ConversationMember> active = members.findActiveMembers(conversationId);
        Map<UUID, ConversationMember> activeById = active.stream()
                .collect(Collectors.toMap(ConversationMember::getUserId, member -> member));
        Map<UUID, ReadReceiptView> byRecipient = new HashMap<>();
        for (Object[] row : senders) {
            ConversationMember recipient = activeById.get(row[0]);
            long readUpTo = (Long) row[1];
            // Alıcının kendi görünürlük sınırının altındaki mesajlar yok sayılır (geçmişte ve /reads'te de gizlidir).
            if (recipient != null && recipient.canSee(readUpTo)) {
                UUID recipientId = recipient.getUserId();
                long allReadUpTo = visibleOrZero(recipient,
                        messages.findOwnMaxSeqUpTo(conversationId, recipientId, ReadWatermark.of(recipientId, active))
                                .orElse(0L));
                byRecipient.put(recipientId, new ReadReceiptView(conversationId, advance.readerId(), readUpTo,
                        advance.readAt(), allReadUpTo));
            }
        }
        if (!byRecipient.isEmpty()) {
            events.publishEvent(new ReadReceiptsCreated(byRecipient));
        }
    }

    /**
     * Üye ayrıldığında ya da çıkarıldığında su seviyesi yükselen göndericilere {@code readerId}, {@code readUpToSeq} ve
     * {@code readAt} alanları {@code null} bir olay hazırlar (spec §5). Ayrılan kişi alıcı olamaz. Çağıranın
     * transaction'ına katılır.
     *
     * @param activeBefore ayrılma uygulanmadan önce alınan aktif üye görüntüsü (ayrılan dahil); ayrılan kişi kimliğiyle
     *                      ayıklanır, böylece sonuç ayrılışın bu nesnelere yansıma anına bağlı olmaz
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void onMemberDeparted(UUID conversationId, List<ConversationMember> activeBefore, UUID departedUserId) {
        List<ConversationMember> remaining = activeBefore.stream()
                .filter(member -> !member.getUserId().equals(departedUserId))
                .toList();
        Map<UUID, ReadReceiptView> byRecipient = new HashMap<>();
        for (ConversationMember sender : remaining) {
            UUID senderId = sender.getUserId();
            long before = ReadWatermark.of(senderId, activeBefore);
            long after = ReadWatermark.of(senderId, remaining);
            if (after <= before) {
                continue;
            }
            long readBefore = visibleOrZero(sender,
                    messages.findOwnMaxSeqUpTo(conversationId, senderId, before).orElse(0L));
            long readAfter = visibleOrZero(sender,
                    messages.findOwnMaxSeqUpTo(conversationId, senderId, after).orElse(0L));
            if (readAfter > readBefore) {
                byRecipient.put(senderId, new ReadReceiptView(conversationId, null, null, null, readAfter));
            }
        }
        if (!byRecipient.isEmpty()) {
            events.publishEvent(new ReadReceiptsCreated(byRecipient));
        }
    }

    /** Üyenin görünürlük sınırının altındaki kendi seq'i yok sayılır (0); sınırdaki ve üstündeki olduğu gibi kalır. */
    private static long visibleOrZero(ConversationMember member, long ownSeq) {
        return member.canSee(ownSeq) ? ownSeq : 0L;
    }
}

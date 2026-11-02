package com.ltz.messaging.application.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.messaging.domain.entity.ConversationMember;
import com.ltz.messaging.domain.entity.Message;
import com.ltz.messaging.domain.entity.MessageReadRange;
import com.ltz.messaging.domain.enums.ReadStatus;
import com.ltz.messaging.domain.exception.MessagingErrorCode;
import com.ltz.messaging.domain.exception.MessagingException;
import com.ltz.messaging.infrastructure.persistence.repository.ConversationMemberRepository;
import com.ltz.messaging.infrastructure.persistence.repository.MessageReadRangeRepository;
import com.ltz.messaging.infrastructure.persistence.repository.MessageRepository;

import lombok.RequiredArgsConstructor;

/**
 * MSG-12 mesaj bilgisi: gönderen, mesajını kimin hangi saatte okuduğunu ve kimin henüz okumadığını görür. Sıra:
 * üyelik (404 {@code CONVERSATION_NOT_FOUND}) → mesaj bu sohbette ve üyeye görünür (404 {@code MESSAGE_NOT_FOUND}) →
 * gönderen (403 {@code NOT_MESSAGE_SENDER}). Böylece gönderen olmayan biri, görmediği mesajın varlığını öğrenemez.
 */
@Service
@RequiredArgsConstructor
public class MessageReadService {

    private final ConversationAccessPolicy access;
    private final ConversationMemberRepository members;
    private final MessageRepository messages;
    private final MessageReadRangeRepository ranges;

    @Transactional(readOnly = true)
    public MessageReadsView reads(UUID viewer, UUID conversationId, UUID messageId) {
        ConversationMember member = access.requireMember(conversationId, viewer);
        Message message = messages.findByIdAndConversationId(messageId, conversationId)
                .filter(found -> member.canSee(found.getSeq()))
                .orElseThrow(() -> new MessagingException(MessagingErrorCode.MESSAGE_NOT_FOUND));
        if (!message.getSenderId().equals(viewer)) {
            throw new MessagingException(MessagingErrorCode.NOT_MESSAGE_SENDER);
        }
        long seq = message.getSeq();
        List<ConversationMember> active = members.findActiveMembers(conversationId);
        // Durum geçmişle aynı formülden gelir; listeler boş olsa bile (spec §3.2 uç durumu) tutarlı kalır.
        ReadStatus status = ReadWatermark.status(seq, ReadWatermark.of(viewer, active));
        List<ConversationMember> eligible = active.stream()
                .filter(candidate -> !candidate.getUserId().equals(viewer) && candidate.canSee(seq))
                .toList();
        List<UUID> pending = eligible.stream()
                .filter(candidate -> candidate.getLastReadSeq() < seq)
                .map(ConversationMember::getUserId)
                .toList();
        return new MessageReadsView(message.getId(), seq, status, readersOf(conversationId, seq, eligible), pending);
    }

    /** Okumuş uygun okuyucular, okuma saatine göre yeniden eskiye; saati bilinmeyenler (aralığı yok) en sonda. */
    private List<MessageReadsView.Reader> readersOf(UUID conversationId, long seq, List<ConversationMember> eligible) {
        List<UUID> readerIds = eligible.stream()
                .filter(candidate -> candidate.getLastReadSeq() >= seq)
                .map(ConversationMember::getUserId)
                .toList();
        if (readerIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, Instant> readAt = ranges.findCovering(conversationId, readerIds, seq).stream()
                .collect(Collectors.toMap(MessageReadRange::getUserId, MessageReadRange::getReadAt,
                        (first, second) -> first));
        // Sıralama kararlıdır: eşit (ya da boş) saatlerde katılma sırası korunur.
        return readerIds.stream()
                .map(userId -> new MessageReadsView.Reader(userId, readAt.get(userId)))
                .sorted(Comparator.comparing(MessageReadsView.Reader::readAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }
}

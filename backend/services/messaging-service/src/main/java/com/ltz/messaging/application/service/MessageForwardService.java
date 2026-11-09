package com.ltz.messaging.application.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.ltz.messaging.domain.entity.Conversation;
import com.ltz.messaging.domain.entity.ConversationMember;
import com.ltz.messaging.domain.entity.Message;
import com.ltz.messaging.domain.entity.MessageAttachment;
import com.ltz.messaging.domain.enums.MessageType;
import com.ltz.messaging.domain.exception.MessagingErrorCode;
import com.ltz.messaging.domain.exception.MessagingException;
import com.ltz.messaging.infrastructure.persistence.repository.ConversationMemberRepository;
import com.ltz.messaging.infrastructure.persistence.repository.ConversationRepository;
import com.ltz.messaging.infrastructure.persistence.repository.MessageAttachmentRepository;
import com.ltz.messaging.infrastructure.persistence.repository.MessageRepository;

import lombok.RequiredArgsConstructor;

/**
 * MSG-20 mesaj iletme. Kaynak, iletenin görebildiği (görünürlük sınırı, benden sil) ve herkesten silinmemiş bir mesaj
 * olmalı. Her hedefte iletenin yazma yetkisi ve birebir sohbette engel kontrolü uygulanır (engel sorgusu transaction
 * dışında); hedeflerden biri uygun değilse hiçbiri gönderilmez. Tüm kopyalar tek transaction'da yazılır: kaynak mesaj
 * paylaşımlı kilitle okunur (iletme sürerken herkesten silinemez), hedef sohbetler kimlik sırasıyla kilitlenir. Her kopya
 * iletenin yeni mesajıdır (yeni {@code seq}, outbox olayı, akış olayı) ve gönderim oranına sayılır. Görselde baytlar
 * kopyalanmaz: hedefte aynı içeriğe yeni bir bağlantı açılır. Hedef başına istemci kimliği istek kimliğinden türetilir;
 * aynı istek tekrarlanırsa mevcut kopyalar döner.
 */
@Service
@RequiredArgsConstructor
public class MessageForwardService {

    private final ConversationAccessPolicy access;
    private final VisibleMessages visible;
    private final ConversationRepository conversations;
    private final ConversationMemberRepository members;
    private final MessageRepository messages;
    private final MessageAttachmentRepository attachments;
    private final MessageAppender appender;
    private final MessageViews views;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public ForwardResult forward(UUID user, UUID sourceConversationId, UUID sourceMessageId, String clientMessageId,
                                 List<UUID> targets) {
        if (new HashSet<>(targets).size() != targets.size()) {
            throw new MessagingException(MessagingErrorCode.DUPLICATE_FORWARD_TARGET);
        }
        Map<UUID, String> clientIds = new LinkedHashMap<>();
        targets.forEach(target -> clientIds.put(target, targetClientId(clientMessageId, sourceMessageId, target)));
        Optional<ForwardResult> replay = Objects.requireNonNull(transactions.execute(
                status -> replay(user, sourceConversationId, sourceMessageId, clientIds)));
        if (replay.isPresent()) {
            return replay.get();
        }
        targets.forEach(target -> access.requireCanWrite(target, user));
        try {
            return Objects.requireNonNull(transactions.execute(
                    status -> insert(user, sourceConversationId, sourceMessageId, clientIds)));
        } catch (DataIntegrityViolationException exception) {
            // Aynı istek eşzamanlı iki kez geldi (tekil indeks kazananı seçti).
            throw new MessagingException(MessagingErrorCode.CLIENT_MESSAGE_ID_REUSED, exception);
        }
    }

    /** Hedef başına istemci kimliği: istek kimliği + kaynak + hedeften (en çok 39 karakter, şemadaki sınırın altında). */
    static String targetClientId(String clientMessageId, UUID sourceMessageId, UUID target) {
        return "fw-" + UUID.nameUUIDFromBytes((clientMessageId + ":" + sourceMessageId + ":" + target)
                .getBytes(StandardCharsets.UTF_8));
    }

    /** Tüm hedeflerde kopya zaten varsa (yeniden deneme) onları döner. */
    private Optional<ForwardResult> replay(UUID user, UUID sourceConversationId, UUID sourceMessageId,
                                           Map<UUID, String> clientIds) {
        visible.require(access.requireMember(sourceConversationId, user), sourceMessageId);
        List<MessageView> existing = new ArrayList<>();
        for (Map.Entry<UUID, String> target : clientIds.entrySet()) {
            Optional<Message> copy = messages.findBySenderIdAndClientMessageId(user, target.getValue());
            if (copy.isEmpty()) {
                return Optional.empty();
            }
            existing.add(existingView(user, target.getKey(), copy.get()));
        }
        return Optional.of(new ForwardResult(existing, false));
    }

    private ForwardResult insert(UUID user, UUID sourceConversationId, UUID sourceMessageId,
                                 Map<UUID, String> clientIds) {
        ConversationMember sourceMember = access.requireMember(sourceConversationId, user);
        Message source = VisibleMessages.requireNotDeleted(visible.check(sourceMember,
                messages.findByIdAndConversationIdForShare(sourceMessageId, sourceConversationId).orElse(null)));
        MessageAttachment sourceAttachment = source.getType() == MessageType.IMAGE
                ? attachments.findByMessageId(source.getId())
                        .orElseThrow(() -> new MessagingException(MessagingErrorCode.MESSAGE_NOT_FOUND))
                : null;

        // Hedefler kimlik sırasıyla kilitlenir: eşzamanlı iki iletme birbirini kilitlemez.
        Map<UUID, Conversation> locked = new HashMap<>();
        clientIds.keySet().stream().sorted().forEach(target -> locked.put(target,
                conversations.findByIdForUpdate(target)
                        .orElseThrow(() -> new MessagingException(MessagingErrorCode.CONVERSATION_NOT_FOUND))));
        Map<UUID, ConversationMember> targetMembers = new HashMap<>();
        clientIds.keySet().forEach(target -> targetMembers.put(target, access.requireMember(target, user)));

        Map<UUID, Message> existing = new HashMap<>();
        clientIds.forEach((target, clientId) -> messages.findBySenderIdAndClientMessageId(user, clientId)
                .ifPresent(copy -> existing.put(target, copy)));
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        appender.requireWithinRate(user, clientIds.size() - existing.size(), now);

        List<MessageView> results = new ArrayList<>();
        for (Map.Entry<UUID, String> entry : clientIds.entrySet()) {
            UUID target = entry.getKey();
            if (existing.containsKey(target)) {
                results.add(existingView(user, target, existing.get(target)));
                continue;
            }
            results.add(appender.append(locked.get(target), targetMembers.get(target), now,
                    seq -> Message.forwardOf(source, target, seq, user, entry.getValue(), now),
                    copy -> {
                        if (sourceAttachment != null) {
                            attachments.saveAndFlush(MessageAttachment.forwardCopy(target, user, copy.getId(),
                                    sourceAttachment, now));
                        }
                    }));
        }
        return new ForwardResult(results, existing.size() < clientIds.size());
    }

    /** Daha önce oluşmuş kopyanın iletene göre görünümü (okundu durumu dahil). */
    private MessageView existingView(UUID user, UUID target, Message copy) {
        ConversationMember member = access.requireMember(target, user);
        long watermark = ReadWatermark.of(user, members.findActiveMembers(target));
        return views.decorate(user, member, MessageView.of(copy)
                .withReadStatus(ReadWatermark.status(copy.getSeq(), watermark)));
    }
}

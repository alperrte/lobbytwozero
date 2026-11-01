package com.ltz.messaging.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.ltz.messaging.application.mapper.ConversationMapper;
import com.ltz.messaging.domain.entity.Conversation;
import com.ltz.messaging.domain.entity.ConversationMember;
import com.ltz.messaging.domain.enums.MemberRole;
import com.ltz.messaging.domain.exception.MessagingErrorCode;
import com.ltz.messaging.domain.exception.MessagingException;
import com.ltz.messaging.infrastructure.config.GroupProperties;
import com.ltz.messaging.infrastructure.persistence.repository.ConversationMemberRepository;
import com.ltz.messaging.infrastructure.persistence.repository.ConversationRepository;
import com.ltz.messaging.infrastructure.persistence.repository.DirectConversationLock;

import lombok.RequiredArgsConstructor;

/**
 * Birebir sohbet açma (MSG-01), grup oluşturma (MSG-02) ve sohbet görüntüleme (MSG-14). Engel sorgusu (MSG-15) ağ
 * çağrısı olduğundan DB transaction'ından önce yapılır; ardından kayıt tek transaction'da yazılır.
 */
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversations;
    private final ConversationMemberRepository members;
    private final DirectConversationLock directLock;
    private final ConversationAccessPolicy access;
    private final BlockGuard blockGuard;
    private final GroupProperties groupProperties;
    private final TransactionTemplate transactions;
    private final Clock clock;

    /**
     * MSG-01. İki kullanıcı arasındaki birebir sohbeti döner; yoksa oluşturur. Aralarında engel varsa (hangi yönde
     * olursa olsun) yeni ya da mevcut sohbet açılamaz. Eşzamanlı açılışlar aynı sohbette buluşur.
     */
    public DirectConversationResult openDirect(UUID initiator, UUID other) {
        if (initiator.equals(other)) {
            throw new MessagingException(MessagingErrorCode.CANNOT_MESSAGE_SELF);
        }
        blockGuard.requireNoBlock(initiator, List.of(other));
        return Objects.requireNonNull(transactions.execute(status -> findOrCreateDirect(initiator, other)));
    }

    /**
     * MSG-02. Oluşturan {@code OWNER} olur, diğerleri {@code MEMBER}. Yinelenen kimlikler tek sayılır; oluşturan
     * listede olamaz. Oluşturan ile engelli olduğu biri gruba eklenemez.
     */
    public ConversationView createGroup(UUID owner, String title, Collection<UUID> memberIds) {
        Set<UUID> invited = new LinkedHashSet<>(memberIds);
        if (invited.contains(owner)) {
            throw new MessagingException(MessagingErrorCode.CANNOT_ADD_SELF);
        }
        if (invited.size() + 1 > groupProperties.maxMembers()) {
            throw new MessagingException(MessagingErrorCode.GROUP_MEMBER_LIMIT_REACHED);
        }
        blockGuard.requireNoBlock(owner, invited);
        return Objects.requireNonNull(transactions.execute(status -> insertGroup(owner, title.strip(), invited)));
    }

    /** MSG-14. Yalnız aktif üye görür; diğer herkes ve olmayan sohbet aynı 404. */
    @Transactional(readOnly = true)
    public ConversationView get(UUID viewer, UUID conversationId) {
        access.requireMember(conversationId, viewer);
        Conversation conversation = conversations.findById(conversationId)
                .orElseThrow(() -> new MessagingException(MessagingErrorCode.CONVERSATION_NOT_FOUND));
        return ConversationMapper.toView(conversation, members.findActiveMembers(conversationId), viewer);
    }

    private DirectConversationResult findOrCreateDirect(UUID initiator, UUID other) {
        String key = Conversation.directKey(initiator, other);
        directLock.acquire(key);
        Optional<Conversation> existing = conversations.findByDirectKey(key);
        if (existing.isPresent()) {
            Conversation conversation = existing.get();
            return new DirectConversationResult(ConversationMapper.toView(conversation,
                    members.findActiveMembers(conversation.getId()), initiator), false);
        }
        Instant now = now();
        Conversation conversation = conversations.save(Conversation.direct(initiator, other, now));
        List<ConversationMember> pair = members.saveAll(List.of(
                ConversationMember.join(conversation.getId(), initiator, MemberRole.MEMBER, now),
                ConversationMember.join(conversation.getId(), other, MemberRole.MEMBER, now)));
        return new DirectConversationResult(ConversationMapper.toView(conversation, pair, initiator), true);
    }

    private ConversationView insertGroup(UUID owner, String title, Set<UUID> invited) {
        Instant now = now();
        Conversation conversation = conversations.save(Conversation.group(title, owner, now));
        List<ConversationMember> joined = new ArrayList<>();
        joined.add(ConversationMember.join(conversation.getId(), owner, MemberRole.OWNER, now));
        for (UUID userId : invited) {
            joined.add(ConversationMember.join(conversation.getId(), userId, MemberRole.MEMBER, now));
        }
        members.saveAll(joined);
        members.flush();
        return ConversationMapper.toView(conversation, members.findActiveMembers(conversation.getId()), owner);
    }

    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}

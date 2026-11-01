package com.ltz.messaging.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

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

import lombok.RequiredArgsConstructor;

/**
 * Grup üyeliği: üye ekleme ve rol yönetimi (MSG-03), gruptan ayrılma (MSG-04), yöneticinin üye çıkarması (MSG-05).
 * Kurallar:
 * <ul>
 *   <li>Yalnız grup sohbetlerinde geçerlidir; birebir sohbette {@code 409 GROUP_CONVERSATION_REQUIRED}.</li>
 *   <li>Sohbetin aktif üyesi olmayan herkes {@code 404 CONVERSATION_NOT_FOUND} alır (MSG-14).</li>
 *   <li>Üye ekleme/çıkarma: {@code OWNER} ve {@code ADMIN}. {@code OWNER} herkesi, {@code ADMIN} yalnız
 *       {@code MEMBER}'ı çıkarır. Rolü yalnız {@code OWNER} değiştirir.</li>
 *   <li>Ekleyen ile eklenen arasında engel varsa ekleme reddedilir (MSG-15); mevcut üyelik engelden etkilenmez.</li>
 *   <li>{@code OWNER} ayrılınca sahiplik en eski {@code ADMIN}'e, yoksa en eski üyeye geçer.</li>
 * </ul>
 * Aynı gruptaki değişiklikler sohbet satırı kilitlenerek sıraya konur; yarışta üye sınırı ve tek sahip kuralı
 * bozulmaz.
 */
@Service
@RequiredArgsConstructor
public class GroupMembershipService {

    /** Sahip devrinde sıra: önce ADMIN'ler, sonra katılma zamanı, eşitlikte kullanıcı kimliği. */
    private static final Comparator<ConversationMember> SUCCESSION = Comparator
            .comparing((ConversationMember member) -> member.getRole() != MemberRole.ADMIN)
            .thenComparing(ConversationMember::getJoinedAt)
            .thenComparing(ConversationMember::getUserId);

    private final ConversationRepository conversations;
    private final ConversationMemberRepository members;
    private final ConversationAccessPolicy access;
    private final BlockGuard blockGuard;
    private final GroupProperties groupProperties;
    private final TransactionTemplate transactions;
    private final Clock clock;

    /**
     * MSG-03. Yetki önce okunur (yetkisiz kişi engel sorgusu tetikleyemez), engel transaction dışında sorulur, sonra
     * kilit altında yetki ve sınır yeniden denetlenip üyeler eklenir. Ayrılmış/çıkarılmış eski üye {@code MEMBER}
     * olarak geri döner. Zaten aktif olan biri varsa hiçbiri eklenmez.
     */
    public ConversationView addMembers(UUID actor, UUID conversationId, Collection<UUID> userIds) {
        Set<UUID> invited = new LinkedHashSet<>(userIds);
        if (invited.contains(actor)) {
            throw new MessagingException(MessagingErrorCode.CANNOT_ADD_SELF);
        }
        transactions.executeWithoutResult(status -> requireManager(conversationId, actor));
        blockGuard.requireNoBlock(actor, invited);
        return Objects.requireNonNull(transactions.execute(status -> insertMembers(actor, conversationId, invited)));
    }

    /** MSG-05. Çıkarılan üyenin sohbete erişimi hemen biter; kendini çıkarmak için ayrılma kullanılır. */
    @Transactional
    public void removeMember(UUID actor, UUID conversationId, UUID target) {
        ConversationMember manager = requireManager(lockGroup(conversationId, actor), actor);
        if (target.equals(actor)) {
            throw new MessagingException(MessagingErrorCode.CANNOT_REMOVE_SELF);
        }
        ConversationMember removed = requireActiveTarget(conversationId, target);
        if (!manager.getRole().canRemove(removed.getRole())) {
            throw new MessagingException(MessagingErrorCode.INSUFFICIENT_GROUP_ROLE);
        }
        removed.removeBy(actor, now());
    }

    /** MSG-03 rol yönetimi: yalnız {@code OWNER}, bir üyeyi {@code ADMIN} ya da {@code MEMBER} yapar. */
    @Transactional
    public ConversationView changeRole(UUID actor, UUID conversationId, UUID target, MemberRole role) {
        Conversation conversation = lockGroup(conversationId, actor);
        if (role == MemberRole.OWNER) {
            throw new MessagingException(MessagingErrorCode.ROLE_NOT_ASSIGNABLE);
        }
        if (access.requireMember(conversationId, actor).getRole() != MemberRole.OWNER) {
            throw new MessagingException(MessagingErrorCode.INSUFFICIENT_GROUP_ROLE);
        }
        if (target.equals(actor)) {
            throw new MessagingException(MessagingErrorCode.CANNOT_CHANGE_OWN_ROLE);
        }
        requireActiveTarget(conversationId, target).changeRole(role);
        return ConversationMapper.toView(conversation, members.findActiveMembers(conversationId), actor);
    }

    /** MSG-04. Ayrılan üyenin erişimi biter; sahip ayrılırsa sahiplik devredilir. Son üye ayrılırsa grup boş kalır. */
    @Transactional
    public void leave(UUID actor, UUID conversationId) {
        lockGroup(conversationId, actor);
        ConversationMember leaving = access.requireMember(conversationId, actor);
        leaving.leave(now());
        if (leaving.getRole() == MemberRole.OWNER) {
            // Eski sahibin ayrılışı önce yazılır: tek aktif sahip kuralı (kısmi tekil indeks) satır satır denetlenir.
            members.flush();
            members.findActiveMembers(conversationId).stream()
                    .min(SUCCESSION)
                    .ifPresent(successor -> successor.changeRole(MemberRole.OWNER));
        }
    }

    private ConversationView insertMembers(UUID actor, UUID conversationId, Set<UUID> invited) {
        Conversation conversation = lockGroup(conversationId, actor);
        requireManager(conversation, actor);
        List<ConversationMember> active = members.findActiveMembers(conversationId);
        if (active.stream().anyMatch(member -> invited.contains(member.getUserId()))) {
            throw new MessagingException(MessagingErrorCode.ALREADY_MEMBER);
        }
        if (active.size() + invited.size() > groupProperties.maxMembers()) {
            throw new MessagingException(MessagingErrorCode.GROUP_MEMBER_LIMIT_REACHED);
        }
        Instant now = now();
        Map<UUID, ConversationMember> former = members.findAllOf(conversationId, invited).stream()
                .collect(Collectors.toMap(ConversationMember::getUserId, Function.identity()));
        for (UUID userId : invited) {
            ConversationMember previous = former.get(userId);
            if (previous == null) {
                members.save(ConversationMember.join(conversationId, userId, MemberRole.MEMBER, now));
            } else {
                previous.rejoin(now);
            }
        }
        members.flush();
        return ConversationMapper.toView(conversation, members.findActiveMembers(conversationId), actor);
    }

    /** Okuma amaçlı yetki ön denetimi (kilitsiz): üye mi, grup mu, yönetici mi. */
    private void requireManager(UUID conversationId, UUID actor) {
        ConversationMember member = access.requireMember(conversationId, actor);
        conversations.findById(conversationId)
                .orElseThrow(() -> new MessagingException(MessagingErrorCode.CONVERSATION_NOT_FOUND))
                .requireGroup();
        requireManagerRole(member);
    }

    private ConversationMember requireManager(Conversation lockedGroup, UUID actor) {
        return requireManagerRole(access.requireMember(lockedGroup.getId(), actor));
    }

    private static ConversationMember requireManagerRole(ConversationMember member) {
        if (!member.getRole().managesMembers()) {
            throw new MessagingException(MessagingErrorCode.INSUFFICIENT_GROUP_ROLE);
        }
        return member;
    }

    /**
     * Sohbeti satır kilidiyle okur. Üyelik kilit alındıktan sonra okunur ki eşzamanlı bir rol değişikliği ya da
     * çıkarma görülsün; tür denetimi üyelikten sonradır ki üye olmayan kişi sohbetin türünü öğrenemesin.
     */
    private Conversation lockGroup(UUID conversationId, UUID actor) {
        Conversation conversation = conversations.findByIdForUpdate(conversationId)
                .orElseThrow(() -> new MessagingException(MessagingErrorCode.CONVERSATION_NOT_FOUND));
        access.requireMember(conversationId, actor);
        conversation.requireGroup();
        return conversation;
    }

    private ConversationMember requireActiveTarget(UUID conversationId, UUID target) {
        return members.findActive(conversationId, target)
                .orElseThrow(() -> new MessagingException(MessagingErrorCode.MEMBER_NOT_FOUND));
    }

    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}

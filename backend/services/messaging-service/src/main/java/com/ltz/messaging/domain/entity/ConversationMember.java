package com.ltz.messaging.domain.entity;

import java.time.Instant;
import java.util.UUID;

import com.ltz.messaging.domain.enums.MemberRole;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Sohbet üyeliği. {@code leftAt} boşsa üye aktiftir; ayrılan (MSG-04) ya da çıkarılan (MSG-05) üyenin satırı silinmez,
 * {@code leftAt} (çıkarıldıysa ayrıca {@code removedBy}) dolar ve sohbete erişimi biter (MSG-14). Yeniden eklenen üye
 * aynı satırla, yeni katılma zamanı ve {@code MEMBER} rolüyle döner.
 */
@Entity
@Table(name = "conversation_members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConversationMember {

    @EmbeddedId
    private ConversationMemberId id;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    private MemberRole role;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    @Column(name = "removed_by")
    private UUID removedBy;

    public static ConversationMember join(UUID conversationId, UUID userId, MemberRole role, Instant now) {
        ConversationMember member = new ConversationMember();
        member.id = new ConversationMemberId(conversationId, userId);
        member.role = role;
        member.joinedAt = now;
        return member;
    }

    public UUID getUserId() {
        return id.getUserId();
    }

    public boolean isActive() {
        return leftAt == null;
    }

    /** Ayrılmış ya da çıkarılmış üyeyi yeniden ekler; önceki rolü geri gelmez. */
    public void rejoin(Instant now) {
        role = MemberRole.MEMBER;
        joinedAt = now;
        leftAt = null;
        removedBy = null;
    }

    public void leave(Instant now) {
        leftAt = now;
    }

    public void removeBy(UUID actor, Instant now) {
        leftAt = now;
        removedBy = actor;
    }

    public void changeRole(MemberRole newRole) {
        role = newRole;
    }
}

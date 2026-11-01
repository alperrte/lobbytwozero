package com.ltz.messaging.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ltz.messaging.domain.enums.ConversationType;
import com.ltz.messaging.domain.enums.MemberRole;
import com.ltz.messaging.domain.exception.MessagingErrorCode;
import com.ltz.messaging.domain.exception.MessagingException;

class ConversationTest {

    private static final UUID HIGH_BIT_SET = new UUID(0x8000_0000_0000_0000L, 0L);
    private static final UUID HIGH_BIT_CLEAR = new UUID(0x0000_0000_0000_0001L, 0L);

    @Test
    void birebirAnahtariYondenBagimsizVePostgresUuidSirasinda() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertThat(Conversation.directKey(first, second)).isEqualTo(Conversation.directKey(second, first));
        // İşaretli karşılaştırmada HIGH_BIT_SET küçük görünürdü; PostgreSQL baytsal (işaretsiz) sıralar.
        assertThat(Conversation.directKey(HIGH_BIT_SET, HIGH_BIT_CLEAR))
                .isEqualTo(HIGH_BIT_CLEAR + ":" + HIGH_BIT_SET);
        UUID sameHigh = new UUID(5L, -1L);
        UUID sameHighSmaller = new UUID(5L, 1L);
        assertThat(Conversation.directKey(sameHigh, sameHighSmaller)).startsWith(sameHighSmaller.toString());
    }

    @Test
    void kendisiyleBirebirSohbetOlmaz() {
        UUID user = UUID.randomUUID();

        assertThatThrownBy(() -> Conversation.direct(user, user, Instant.now()))
                .isInstanceOfSatisfying(MessagingException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(MessagingErrorCode.CANNOT_MESSAGE_SELF));
    }

    @Test
    void birebirSohbetGrupIslemiReddeder() {
        Conversation direct = Conversation.direct(UUID.randomUUID(), UUID.randomUUID(), Instant.now());
        Conversation group = Conversation.group("Takım", UUID.randomUUID(), Instant.now());

        assertThat(direct.getType()).isEqualTo(ConversationType.DIRECT);
        assertThat(direct.getTitle()).isNull();
        assertThatThrownBy(direct::requireGroup).isInstanceOf(MessagingException.class);
        group.requireGroup();
        assertThat(group.getDirectKey()).isNull();
    }

    @Test
    void rolYetkiMatrisi() {
        assertThat(MemberRole.OWNER.managesMembers()).isTrue();
        assertThat(MemberRole.ADMIN.managesMembers()).isTrue();
        assertThat(MemberRole.MEMBER.managesMembers()).isFalse();

        assertThat(MemberRole.OWNER.canRemove(MemberRole.ADMIN)).isTrue();
        assertThat(MemberRole.OWNER.canRemove(MemberRole.MEMBER)).isTrue();
        assertThat(MemberRole.ADMIN.canRemove(MemberRole.MEMBER)).isTrue();
        assertThat(MemberRole.ADMIN.canRemove(MemberRole.ADMIN)).isFalse();
        assertThat(MemberRole.ADMIN.canRemove(MemberRole.OWNER)).isFalse();
        assertThat(MemberRole.MEMBER.canRemove(MemberRole.MEMBER)).isFalse();
    }

    @Test
    void uyelikAyrilmaCikarmaVeYenidenKatilma() {
        Instant joined = Instant.parse("2026-11-01T10:00:00Z");
        UUID admin = UUID.randomUUID();
        ConversationMember member = ConversationMember.join(UUID.randomUUID(), UUID.randomUUID(), MemberRole.ADMIN,
                joined);

        member.removeBy(admin, joined.plusSeconds(5));
        assertThat(member.isActive()).isFalse();
        assertThat(member.getRemovedBy()).isEqualTo(admin);

        member.rejoin(joined.plusSeconds(10));
        assertThat(member.isActive()).isTrue();
        assertThat(member.getRole()).isEqualTo(MemberRole.MEMBER);
        assertThat(member.getRemovedBy()).isNull();
        assertThat(member.getJoinedAt()).isEqualTo(joined.plusSeconds(10));

        member.leave(joined.plusSeconds(20));
        assertThat(member.isActive()).isFalse();
        assertThat(member.getRemovedBy()).isNull();
    }
}

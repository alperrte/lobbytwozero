package com.ltz.messaging.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ltz.messaging.domain.entity.ConversationMember;
import com.ltz.messaging.domain.enums.MemberRole;
import com.ltz.messaging.domain.enums.ReadStatus;

/** MSG-11 su seviyesi formülü (spec §3.2): diğer aktif üyelerin en gerideki "okumuş sayılan" sırası. */
class ReadWatermarkTest {

    private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");

    private final UUID conversation = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();

    @Test
    void digerUyeYoksaSifir() {
        ConversationMember alone = member(owner, 1, 0);

        assertThat(ReadWatermark.of(owner, List.of(alone))).isZero();
        assertThat(ReadWatermark.of(owner, List.of())).isZero();
    }

    @Test
    void enGeridekiUyeBelirler() {
        ConversationMember sender = member(owner, 1, 9);
        ConversationMember bob = member(UUID.randomUUID(), 1, 5);
        ConversationMember carol = member(UUID.randomUUID(), 1, 3);

        assertThat(ReadWatermark.of(owner, List.of(sender, bob, carol))).isEqualTo(3);
    }

    @Test
    void sonradanKatilanEskiMesajiBloklamaz() {
        ConversationMember sender = member(owner, 1, 0);
        ConversationMember bob = member(UUID.randomUUID(), 1, 10);
        ConversationMember latecomer = member(UUID.randomUUID(), 8, 0);

        assertThat(ReadWatermark.of(owner, List.of(sender, bob, latecomer))).isEqualTo(7);
    }

    @Test
    void yenidenEklenenUyeEskiyiOkumusSayilmaz() {
        ConversationMember bob = member(UUID.randomUUID(), 1, 10);
        ConversationMember rejoined = member(UUID.randomUUID(), 1, 0);
        // rejoin işaretçiyi max(0, 8 - 1) = 7'ye çeker; formül aynı sonucu max(7, 7) ile verir.
        rejoined.rejoin(NOW, 8);

        assertThat(ReadWatermark.of(owner, List.of(bob, rejoined))).isEqualTo(7);
    }

    @Test
    void statusSinirda() {
        assertThat(ReadWatermark.status(7, 7)).isEqualTo(ReadStatus.READ);
        assertThat(ReadWatermark.status(8, 7)).isEqualTo(ReadStatus.SENT);
        assertThat(ReadWatermark.status(1, 0)).isEqualTo(ReadStatus.SENT);
    }

    private ConversationMember member(UUID userId, long visibleFrom, long lastRead) {
        ConversationMember member = ConversationMember.join(conversation, userId, MemberRole.MEMBER, NOW, visibleFrom);
        member.markRead(lastRead);
        return member;
    }
}

package com.ltz.messaging.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ltz.messaging.IntegrationTestSupport;
import com.ltz.messaging.domain.entity.MessageReadRange;

/** Okuma aralığı sorgusu gerçek PostgreSQL'de: kapalı aralık, üye ve sohbet süzgeci. */
class MessageReadRangeRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private MessageReadRangeRepository ranges;

    @Test
    void kapsayanAraliklariUyeVeSohbetIcinSinirlariDahilBulur() {
        UUID group = insertConversation();
        UUID other = insertConversation();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        Instant readAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        ranges.save(MessageReadRange.of(group, alice, 1, 3, readAt));
        ranges.save(MessageReadRange.of(group, alice, 4, 9, readAt));
        ranges.save(MessageReadRange.of(group, bob, 4, 4, readAt));
        ranges.save(MessageReadRange.of(other, alice, 1, 9, readAt));

        List<MessageReadRange> covering = ranges.findCovering(group, List.of(alice, bob), 4);

        assertThat(covering).extracting(MessageReadRange::getUserId).containsExactlyInAnyOrder(alice, bob);
        assertThat(covering).allSatisfy(range -> {
            assertThat(range.getConversationId()).isEqualTo(group);
            assertThat(range.getReadAt()).isEqualTo(readAt);
        });
        assertThat(ranges.findCovering(group, List.of(alice), 3)).extracting(MessageReadRange::getFromSeq)
                .containsExactly(1L);
        assertThat(ranges.findCovering(group, List.of(alice), 9)).extracting(MessageReadRange::getToSeq)
                .containsExactly(9L);
        assertThat(ranges.findCovering(group, List.of(alice, bob), 10)).isEmpty();
        assertThat(ranges.findCovering(group, List.of(bob), 5)).isEmpty();
    }

    private UUID insertConversation() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into conversations (id, type, title, created_by, created_at, last_activity_at) "
                + "values (?, 'GROUP', 'Takım', ?, now(), now())", id, UUID.randomUUID());
        return id;
    }
}

package com.ltz.social.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ltz.social.domain.enums.OutboxStatus;
import com.ltz.social.domain.event.FriendRequestSentEvent;
import com.ltz.social.domain.event.FriendshipChangedEvent;

class OutboxEventTest {

    private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");
    private static final Duration BASE = Duration.ofSeconds(30);
    private static final Duration MAX = Duration.ofMinutes(10);
    private static final int MAX_ATTEMPTS = 5;

    private OutboxEvent pending() {
        return OutboxEvent.pending(new FriendRequestSentEvent(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID()), "{}", NOW);
    }

    @Test
    void yeniOlayBekleyenVeHemenTeslimEdilebilirdir() {
        OutboxEvent event = pending();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getAttempts()).isZero();
        assertThat(event.getNextAttemptAt()).isEqualTo(NOW);
        assertThat(event.getEventType()).isEqualTo("FriendRequestSent");
        assertThat(event.getEventVersion()).isEqualTo(1);
        assertThat(event.getAggregateType()).isEqualTo("FriendRequest");
    }

    @Test
    void kiraSonrakiDenemeyiOteler() {
        OutboxEvent event = pending();

        event.lease(NOW, Duration.ofMinutes(1));

        assertThat(event.getNextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(1)));
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    void basariliTeslimGonderildiOlarakIsaretlenir() {
        OutboxEvent event = pending();

        event.markSent(NOW.plusSeconds(5));

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(event.getSentAt()).isEqualTo(NOW.plusSeconds(5));
        assertThat(event.getNextAttemptAt()).isNull();
    }

    @Test
    void basarisizTeslimUstelGeriCekilmeyleYenidenPlanlanir_ustSinirliDir() {
        OutboxEvent event = pending();

        event.markAttemptFailed(NOW, MAX_ATTEMPTS + 10, BASE, MAX);
        assertThat(event.getNextAttemptAt()).isEqualTo(NOW.plus(BASE));
        event.markAttemptFailed(NOW, MAX_ATTEMPTS + 10, BASE, MAX);
        assertThat(event.getNextAttemptAt()).isEqualTo(NOW.plus(BASE.multipliedBy(2)));
        event.markAttemptFailed(NOW, MAX_ATTEMPTS + 10, BASE, MAX);
        assertThat(event.getNextAttemptAt()).isEqualTo(NOW.plus(BASE.multipliedBy(4)));
        for (int i = 0; i < 6; i++) {
            event.markAttemptFailed(NOW, MAX_ATTEMPTS + 10, BASE, MAX);
        }

        assertThat(event.getNextAttemptAt()).isEqualTo(NOW.plus(MAX));
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getAttempts()).isEqualTo(9);
    }

    @Test
    void enFazlaDenemeAsilincaFAILEDolurVeYenidenPlanlanmaz() {
        OutboxEvent event = pending();

        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            event.markAttemptFailed(NOW, MAX_ATTEMPTS, BASE, MAX);
        }

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event.getAttempts()).isEqualTo(MAX_ATTEMPTS);
        assertThat(event.getNextAttemptAt()).isNull();
    }

    @Test
    void olayKaynagiOlayTurundenTurer_arkadaslikKimligiSiradanBagimsiz() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        FriendshipChangedEvent forward = FriendshipChangedEvent.of(first, second, FriendshipChangedEvent.Change.ADDED);
        FriendshipChangedEvent backward = FriendshipChangedEvent.of(second, first,
                FriendshipChangedEvent.Change.ADDED);

        assertThat(forward).isEqualTo(backward);
        assertThat(forward.aggregateId()).isEqualTo(backward.aggregateId());
        assertThat(forward.eventType()).isEqualTo("FriendshipChanged");
        assertThat(forward.aggregateType()).isEqualTo("Friendship");
    }
}

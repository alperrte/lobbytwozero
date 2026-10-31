package com.ltz.auth.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ltz.auth.domain.enums.AuthEventStatus;
import com.ltz.auth.domain.enums.AuthEventType;

class AuthEventTest {

    private static final Instant NOW = Instant.parse("2026-10-29T10:00:00Z");
    private static final Duration LEASE = Duration.ofMinutes(2);
    private static final Duration INITIAL = Duration.ofSeconds(10);
    private static final Duration MAX = Duration.ofHours(1);

    private AuthEvent statusEvent() {
        return AuthEvent.pending(UUID.randomUUID(), UUID.randomUUID(), AuthEventType.ACCOUNT_STATUS_CHANGED, "BANNED",
                "community", NOW);
    }

    @Test
    void degerGerektirenOlayDegersizOlusmazDegerTasimayanOlayDegerSaklamaz() {
        UUID eventId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        assertThatThrownBy(() -> AuthEvent.pending(eventId, accountId, AuthEventType.USER_REGISTERED, null, "user", NOW))
                .isInstanceOf(NullPointerException.class);

        AuthEvent emailChanged = AuthEvent.pending(UUID.randomUUID(), UUID.randomUUID(),
                AuthEventType.ACCOUNT_EMAIL_CHANGED, "gizli@ornek.test", "user", NOW);

        assertThat(emailChanged.getValue()).as("değer taşımayan türde değer saklanmaz").isNull();
    }

    @Test
    void yeniOlayBeklemedeVeHemenVadesindedir() {
        AuthEvent event = statusEvent();

        assertThat(event.isPending()).isTrue();
        assertThat(event.getNextAttemptAt()).isEqualTo(NOW);
        assertThat(event.getAttempts()).isZero();
    }

    @Test
    void kiralamaDenemeyiSayarVeSatiriIleriAlir() {
        AuthEvent event = statusEvent();

        event.claim(NOW, LEASE);

        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getNextAttemptAt()).isEqualTo(NOW.plus(LEASE));
    }

    @Test
    void hataUstelGeriCekilmeUygularVeUstSiniriAsmaz() {
        AuthEvent event = statusEvent();
        event.claim(NOW, LEASE);
        event.fail(NOW, "EVENT_UNREACHABLE", INITIAL, MAX);
        assertThat(event.getNextAttemptAt()).isEqualTo(NOW.plus(INITIAL));
        assertThat(event.getLastError()).isEqualTo("EVENT_UNREACHABLE");

        event.claim(NOW, LEASE);
        event.fail(NOW, "EVENT_HTTP_503", INITIAL, MAX);
        assertThat(event.getNextAttemptAt()).isEqualTo(NOW.plus(INITIAL.multipliedBy(2)));

        for (int attempt = 0; attempt < 40; attempt++) {
            event.claim(NOW, LEASE);
            event.fail(NOW, "EVENT_HTTP_503", INITIAL, MAX);
        }
        assertThat(event.getNextAttemptAt()).isEqualTo(NOW.plus(MAX));
    }

    @Test
    void teslimDurumuDegistirirHataKodunuTemizler() {
        AuthEvent event = statusEvent();
        event.claim(NOW, LEASE);
        event.fail(NOW, "EVENT_FAILED", INITIAL, MAX);

        event.deliver(NOW.plusSeconds(30));

        assertThat(event.getStatus()).isEqualTo(AuthEventStatus.DELIVERED);
        assertThat(event.isPending()).isFalse();
        assertThat(event.getDeliveredAt()).isEqualTo(NOW.plusSeconds(30));
        assertThat(event.getLastError()).isNull();
    }
}

package com.ltz.messaging.infrastructure.messaging.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.ltz.messaging.application.service.MessageView;
import com.ltz.messaging.domain.enums.MessageType;
import com.ltz.messaging.domain.exception.MessagingErrorCode;
import com.ltz.messaging.domain.exception.MessagingException;
import com.ltz.messaging.infrastructure.config.StreamProperties;

/** Akış kaydı: ömür hesabı, kullanıcı sınırı, dolu kuyrukta kapatma ve yazma hatasında kayıttan çıkarma. */
class SseMessageHubTest {

    private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final StreamProperties PROPERTIES =
            new StreamProperties(Duration.ofSeconds(4), Duration.ofMinutes(30), 2);
    private static final Executor DIRECT = Runnable::run;

    private final UUID user = UUID.randomUUID();

    @Test
    void omurTokenBitisiIleUstSinirinKucuguEnAzBesSaniye() {
        SseMessageHub hub = new SseMessageHub(PROPERTIES, DIRECT, CLOCK);

        assertThat(hub.open(user, NOW.plusSeconds(60)).getTimeout()).isEqualTo(60_000L);
        assertThat(hub.open(UUID.randomUUID(), NOW.plus(Duration.ofHours(5))).getTimeout())
                .isEqualTo(Duration.ofMinutes(30).toMillis());
        assertThat(hub.open(UUID.randomUUID(), NOW.minusSeconds(10)).getTimeout()).isEqualTo(5_000L);
    }

    @Test
    void kullaniciBasinaSinirAsilamaz() {
        SseMessageHub hub = new SseMessageHub(PROPERTIES, DIRECT, CLOCK);
        hub.open(user, NOW.plusSeconds(60));
        hub.open(user, NOW.plusSeconds(60));

        assertThatThrownBy(() -> hub.open(user, NOW.plusSeconds(60)))
                .isInstanceOfSatisfying(MessagingException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(MessagingErrorCode.TOO_MANY_STREAMS));
        assertThat(hub.openStreams(user)).isEqualTo(2);
    }

    @Test
    void kuyrukDoluysaAlicininAkislariKapanir() {
        Executor full = task -> {
            throw new RejectedExecutionException("full");
        };
        SseMessageHub hub = new SseMessageHub(PROPERTIES, full, CLOCK);
        hub.open(user, NOW.plusSeconds(60));

        hub.publish(List.of(user), message());

        assertThat(hub.openStreams(user)).isZero();
    }

    @Test
    void yazilamayanAkisKayittanCikarilirDigerleriEtkilenmez() {
        SseMessageHub hub = new SseMessageHub(PROPERTIES, DIRECT, CLOCK);
        UUID other = UUID.randomUUID();
        SseEmitter gone = hub.open(user, NOW.plusSeconds(60));
        hub.open(other, NOW.plusSeconds(60));
        // Tamamlanmış akışa yazma, kopmuş istemcide olduğu gibi hata verir.
        gone.complete();

        hub.heartbeat();
        hub.publish(List.of(user, other), message());

        assertThat(hub.openStreams(user)).isZero();
        assertThat(hub.openStreams(other)).isEqualTo(1);
    }

    private static MessageView message() {
        return new MessageView(UUID.randomUUID(), UUID.randomUUID(), 1, UUID.randomUUID(), MessageType.TEXT, "x",
                "c", NOW);
    }
}

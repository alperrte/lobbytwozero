package com.ltz.messaging.infrastructure.messaging.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.ltz.messaging.application.port.MessageStreamPort;
import com.ltz.messaging.application.service.MessageView;
import com.ltz.messaging.application.service.ReactionsView;
import com.ltz.messaging.application.service.ReadReceiptView;
import com.ltz.messaging.domain.enums.MessageType;
import com.ltz.messaging.domain.exception.MessagingErrorCode;
import com.ltz.messaging.domain.exception.MessagingException;
import com.ltz.messaging.infrastructure.config.StreamProperties;

/**
 * Akış kaydı: ömür hesabı, kullanıcı sınırı, dolu kuyrukta kapatma ve yazma hatasında kayıttan çıkarma; mesaj ve
 * okundu ({@code read}) olayları için aynı kurallar.
 */
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

        hub.publish(Map.of(user, message()));

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
        hub.publish(Map.of(user, message(), other, message()));

        assertThat(hub.openStreams(user)).isZero();
        assertThat(hub.openStreams(other)).isEqualTo(1);
    }

    @Test
    void okunduOlayiAdiRead() {
        assertThat(SseMessageHub.READ_EVENT).isEqualTo("read");
    }

    @Test
    void okunduOlayindaKuyrukDoluysaYalnizAlicininAkislariKapanir() {
        Executor full = task -> {
            throw new RejectedExecutionException("full");
        };
        SseMessageHub hub = new SseMessageHub(PROPERTIES, full, CLOCK);
        UUID other = UUID.randomUUID();
        hub.open(user, NOW.plusSeconds(60));
        hub.open(other, NOW.plusSeconds(60));

        hub.publishReadReceipt(user, receipt());

        assertThat(hub.openStreams(user)).isZero();
        assertThat(hub.openStreams(other)).isEqualTo(1);
    }

    @Test
    void okunduOlayiYazilamayanAkistaKayittanCikarirAkisiOlmayanAliciyiAtlar() {
        SseMessageHub hub = new SseMessageHub(PROPERTIES, DIRECT, CLOCK);
        SseEmitter gone = hub.open(user, NOW.plusSeconds(60));
        hub.open(user, NOW.plusSeconds(60));
        // Tamamlanmış akışa yazma hata verir; aynı kullanıcının diğer akışı açık kalır.
        gone.complete();

        hub.publishReadReceipt(user, receipt());
        hub.publishReadReceipt(UUID.randomUUID(), receipt());

        assertThat(hub.openStreams(user)).isEqualTo(1);
    }

    @Test
    void degisiklikOlayiYazilamayanAkistaKayittanCikarirAkisiOlmayanAliciyiAtlar() {
        SseMessageHub hub = new SseMessageHub(PROPERTIES, DIRECT, CLOCK);
        SseEmitter gone = hub.open(user, NOW.plusSeconds(60));
        hub.open(user, NOW.plusSeconds(60));
        gone.complete();

        hub.publishEvent(MessageStreamPort.DELETED_EVENT, Map.of(user, Map.of("seq", 1), UUID.randomUUID(), Map.of()));

        assertThat(hub.openStreams(user)).isEqualTo(1);
    }

    @Test
    void degisiklikOlayindaKuyrukDoluysaAlicininAkislariKapanir() {
        Executor full = task -> {
            throw new RejectedExecutionException("full");
        };
        SseMessageHub hub = new SseMessageHub(PROPERTIES, full, CLOCK);
        hub.open(user, NOW.plusSeconds(60));

        hub.publishEvent(MessageStreamPort.REACTION_EVENT, Map.of(user, Map.of()));

        assertThat(hub.openStreams(user)).isZero();
    }

    private static ReadReceiptView receipt() {
        return new ReadReceiptView(UUID.randomUUID(), UUID.randomUUID(), 3L, NOW, 3);
    }

    private static MessageView message() {
        return new MessageView(UUID.randomUUID(), UUID.randomUUID(), 1, UUID.randomUUID(), MessageType.TEXT, "x",
                "c", NOW, null, null, null, null, null, 0, false, ReactionsView.EMPTY, null);
    }
}

package com.ltz.messaging.infrastructure.messaging.realtime;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.ltz.messaging.application.port.MessageStreamPort;
import com.ltz.messaging.application.service.MessageView;
import com.ltz.messaging.domain.exception.MessagingErrorCode;
import com.ltz.messaging.domain.exception.MessagingException;
import com.ltz.messaging.infrastructure.config.StreamProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * SSE akışlarının bu örnekteki kaydı (docs/decisions/0001-gercek-zamanli-mesajlasma.md). Her kullanıcının en fazla
 * {@code maxPerUser} akışı olur. İletim ayrı bir yürütücüde yapılır (yavaş istemci gönderim isteğini bekletmez);
 * yürütücü doluysa ya da yazma başarısızsa akış kapatılır, istemci yeniden bağlanıp geçmişten eşitlenir. Kalp atışı
 * ({@code heartbeat}) bağlantıyı aradaki proxy'lerin zaman aşımına karşı canlı tutar. Kayıt bellektedir: birden çok
 * örnekte çalışırken örnekler arası yayın gerekir (ADR'de açık nokta). Mesaj içeriği loglanmaz.
 */
@Slf4j
public class SseMessageHub implements MessageStreamPort {

    static final String MESSAGE_EVENT = "message";
    static final String READY_EVENT = "ready";
    /** Token bitişine çok yakın açılan akış hemen kapanmasın diye en kısa ömür. */
    static final Duration MIN_LIFETIME = Duration.ofSeconds(5);

    private final Map<UUID, Set<SseEmitter>> streams = new ConcurrentHashMap<>();
    private final StreamProperties properties;
    private final Executor executor;
    private final Clock clock;

    public SseMessageHub(StreamProperties properties, Executor executor, Clock clock) {
        this.properties = properties;
        this.executor = executor;
        this.clock = clock;
    }

    @Override
    public SseEmitter open(UUID userId, Instant closeBy) {
        Duration untilExpiry = Duration.between(clock.instant(), closeBy);
        Duration lifetime = untilExpiry.compareTo(properties.maxDuration()) < 0 ? untilExpiry : properties.maxDuration();
        SseEmitter emitter = new SseEmitter(Math.max(lifetime.toMillis(), MIN_LIFETIME.toMillis()));
        streams.compute(userId, (key, current) -> {
            Set<SseEmitter> open = current == null ? ConcurrentHashMap.newKeySet() : current;
            if (open.size() >= properties.maxPerUser()) {
                throw new MessagingException(MessagingErrorCode.TOO_MANY_STREAMS);
            }
            open.add(emitter);
            return open;
        });
        Runnable remove = () -> remove(userId, emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(emitter::complete);
        emitter.onError(failure -> remove.run());
        // İlk olay yanıt başlıklarını hemen gönderir; istemci bağlantının kurulduğunu bilir.
        send(userId, emitter, SseEmitter.event().name(READY_EVENT)
                .data("{\"heartbeatSeconds\":" + properties.heartbeat().toSeconds() + "}", MediaType.APPLICATION_JSON));
        return emitter;
    }

    @Override
    public void publish(Collection<UUID> recipients, MessageView message) {
        try {
            executor.execute(() -> deliver(recipients, message));
        } catch (RejectedExecutionException _) {
            // Kuyruk dolu: alıcıların akışları kapanır, istemciler yeniden bağlanıp geçmişten eşitlenir (mesaj kaybolmaz).
            log.warn("Realtime dispatch queue is full; closing affected streams");
            recipients.forEach(this::closeAll);
        }
    }

    /** Aradaki proxy'lerin okuma zaman aşımına karşı boş yorum satırı gönderir. */
    @Scheduled(fixedDelayString = "${ltz.messaging.stream.heartbeat}")
    public void heartbeat() {
        streams.forEach((userId, emitters) -> emitters.forEach(emitter -> send(userId, emitter,
                SseEmitter.event().comment("hb"))));
    }

    /** Bu örnekteki açık akış sayısı (testler ve izleme için). */
    public int openStreams(UUID userId) {
        Set<SseEmitter> open = streams.get(userId);
        return open == null ? 0 : open.size();
    }

    private void deliver(Collection<UUID> recipients, MessageView message) {
        for (UUID recipient : recipients) {
            Set<SseEmitter> open = streams.get(recipient);
            if (open == null) {
                continue;
            }
            for (SseEmitter emitter : open) {
                send(recipient, emitter, SseEmitter.event().name(MESSAGE_EVENT)
                        .id(message.conversationId() + ":" + message.seq())
                        .data(message, MediaType.APPLICATION_JSON));
            }
        }
    }

    private void send(UUID userId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | RuntimeException exception) {
            // İstemci gitmiş, akış kapanmış ya da olay yazılamadı: yalnız bu akış kayıttan çıkar, diğer alıcılara
            // iletim sürer; istemci yeniden bağlanınca geçmişten eşitlenir.
            remove(userId, emitter);
            emitter.completeWithError(exception);
        }
    }

    private void closeAll(UUID userId) {
        Set<SseEmitter> open = streams.remove(userId);
        if (open != null) {
            open.forEach(SseEmitter::complete);
        }
    }

    private void remove(UUID userId, SseEmitter emitter) {
        streams.computeIfPresent(userId, (key, open) -> {
            open.remove(emitter);
            return open.isEmpty() ? null : open;
        });
    }
}

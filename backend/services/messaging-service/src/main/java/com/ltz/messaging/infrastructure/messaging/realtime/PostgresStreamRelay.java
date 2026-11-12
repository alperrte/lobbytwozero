package com.ltz.messaging.infrastructure.messaging.realtime;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.ltz.messaging.application.port.MessageStreamPort;
import com.ltz.messaging.application.service.MessageView;
import com.ltz.messaging.application.service.ReadReceiptView;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * Birden çok örnekte canlı akış (ADR 0001): olaylar alıcı başına JSON'a çevrilip tek satır olarak
 * {@code stream_relay_events}'e yazılır ve kimliği aynı transaction'da {@code pg_notify} ile duyurulur. Her örnekteki
 * {@link StreamRelayListener} satırı okuyup yalnız kendi akışlarına iletir (bu örnek dahil). Akış kapatma
 * ({@link #closeStreams}) içerik taşımadığından tabloya yazılmaz, doğrudan bildirimle gider. Yazma ayrı yürütücüde
 * yapılır (gönderim isteği DB yazımını beklemez); yazılamazsa olay kaybolur ama mesaj kaybolmaz: istemci geçmişten
 * eşitlenir. Akış açma ve kalp atışı bu örnekte kalır. Kayıtlar 5 dakika sonra silinir.
 */
@Slf4j
public class PostgresStreamRelay implements MessageStreamPort {

    static final String CHANNEL = "ltz_messaging_stream";
    static final String CLOSE_PREFIX = "close:";
    private static final String RETENTION = "5 minutes";

    private final SseMessageHub hub;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ObjectMapper mapper;
    private final Executor executor;

    public PostgresStreamRelay(SseMessageHub hub, JdbcTemplate jdbc, TransactionTemplate transactions,
                               ObjectMapper mapper, Executor executor) {
        this.hub = hub;
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.mapper = mapper;
        this.executor = executor;
    }

    @Override
    public SseEmitter open(UUID userId, Instant closeBy) {
        return hub.open(userId, closeBy);
    }

    @Override
    public void publish(Map<UUID, MessageView> perRecipient) {
        List<StreamEnvelope> envelopes = new ArrayList<>(perRecipient.size());
        perRecipient.forEach((recipient, message) -> envelopes.add(new StreamEnvelope(recipient,
                SseMessageHub.MESSAGE_EVENT, SseMessageHub.messageEventId(message),
                json(SseMessageHub.messagePayload(recipient, message)))));
        relay(envelopes);
    }

    @Override
    public void publishEvent(String eventName, Map<UUID, ?> payloadByRecipient) {
        List<StreamEnvelope> envelopes = new ArrayList<>(payloadByRecipient.size());
        payloadByRecipient.forEach((recipient, payload) ->
                envelopes.add(new StreamEnvelope(recipient, eventName, null, json(payload))));
        relay(envelopes);
    }

    @Override
    public void publishReadReceipt(UUID recipient, ReadReceiptView receipt) {
        relay(List.of(new StreamEnvelope(recipient, SseMessageHub.READ_EVENT, null, json(receipt))));
    }

    @Override
    public void closeStreams(UUID userId) {
        dispatch(() -> notify(CLOSE_PREFIX + userId), () -> hub.closeStreams(userId));
    }

    /** Süresi dolmuş aktarım kayıtları (her örnek çalıştırabilir; idempotent). */
    @Scheduled(fixedDelay = 60_000)
    public void deleteExpired() {
        jdbc.update("delete from stream_relay_events where created_at < now() - cast(? as interval)", RETENTION);
    }

    private void relay(List<StreamEnvelope> envelopes) {
        if (envelopes.isEmpty()) {
            return;
        }
        String payload = mapper.writeValueAsString(envelopes);
        dispatch(() -> transactions.executeWithoutResult(status -> {
            Long id = jdbc.queryForObject("insert into stream_relay_events (payload) values (?) returning id",
                    Long.class, payload);
            notify(String.valueOf(id));
        }), () -> envelopes.stream().map(StreamEnvelope::recipient).distinct().forEach(hub::closeStreams));
    }

    private void notify(String message) {
        jdbc.query("select pg_notify(?, ?)", resultSet -> null, CHANNEL, message);
    }

    /**
     * Aktarım en iyi çabadır. Kuyruk doluysa ya da DB'ye yazılamazsa bu örnekteki alıcı akışları kapatılır: istemci
     * yeniden bağlanıp geçmişten eşitlenir (diğer örneklerdeki akışlar kalp atışıyla açık kalır; kaçan olayı sonraki
     * yeniden bağlanmada alırlar).
     */
    private void dispatch(Runnable write, Runnable fallback) {
        try {
            executor.execute(() -> {
                try {
                    write.run();
                } catch (DataAccessException exception) {
                    log.warn("Realtime relay write failed: {}", exception.getClass().getSimpleName());
                    fallback.run();
                }
            });
        } catch (RejectedExecutionException _) {
            log.warn("Realtime relay queue is full; closing affected local streams");
            fallback.run();
        }
    }

    private String json(Object payload) {
        return mapper.writeValueAsString(payload);
    }
}

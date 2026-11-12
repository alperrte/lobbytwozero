package com.ltz.messaging.infrastructure.messaging.realtime;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.springframework.context.SmartLifecycle;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Bu örneğin aktarım dinleyicisi (ADR 0001). Bağlantı havuzunu küçültmemek için havuz dışı tek bir bağlantıyla
 * {@code LISTEN ltz_messaging_stream} yapar; her bildirimde ilgili {@code stream_relay_events} satırını okuyup
 * {@link LocalStreamDelivery#deliver} ile yalnız bu örnekteki akışlara iletir. Bağlantı koparsa geri çekilmeyle
 * (en çok 30 sn) yeniden bağlanır ve bu örnekteki tüm akışları kapatır: arada kaçan olay olabileceğinden istemciler
 * yeniden bağlanıp geçmişten eşitlenir. İçerik ve kullanıcı kimliği loglanmaz.
 */
@Slf4j
public class StreamRelayListener implements SmartLifecycle {

    private static final Duration MAX_BACKOFF = Duration.ofSeconds(30);
    private static final int POLL_MILLIS = 1000;

    private final String url;
    private final String username;
    private final String password;
    private final LocalStreamDelivery delivery;
    private final Duration initialBackoff;
    private final ObjectMapper mapper = JsonMapper.builder().build();

    private volatile boolean running;
    private volatile boolean listening;
    /** İletilmek üzere teslim edilen son aktarım kaydı (izleme ve testlerin beklemesi için). */
    private volatile long lastHandledId;
    private Thread thread;

    public StreamRelayListener(String url, String username, String password, LocalStreamDelivery delivery,
                               Duration initialBackoff) {
        this.url = url;
        this.username = username;
        this.password = password;
        this.delivery = delivery;
        this.initialBackoff = initialBackoff;
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        thread = Thread.ofPlatform().name("message-stream-relay").daemon().start(this::run);
    }

    @Override
    public synchronized void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(Duration.ofSeconds(5));
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
            }
        }
        listening = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    public long lastHandledId() {
        return lastHandledId;
    }

    /** Şu an bildirim dinleniyor mu (testler ve izleme için). */
    public boolean isListening() {
        return listening;
    }

    private void run() {
        Duration backoff = initialBackoff;
        boolean lostConnection = false;
        while (running) {
            try (Connection connection = DriverManager.getConnection(url, username, password)) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("LISTEN " + PostgresStreamRelay.CHANNEL);
                }
                if (lostConnection) {
                    // Bağlantısızken kaçan olaylar olabilir: istemciler yeniden bağlanıp eşitlensin.
                    delivery.closeAllLocal();
                }
                listening = true;
                backoff = initialBackoff;
                listen(connection);
            } catch (SQLException exception) {
                if (!running) {
                    break;
                }
                log.warn("Realtime relay connection lost: {}", exception.getSQLState());
                listening = false;
                lostConnection = true;
                pause(backoff);
                backoff = backoff.multipliedBy(2).compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : backoff.multipliedBy(2);
            }
        }
        listening = false;
    }

    private void listen(Connection connection) throws SQLException {
        PGConnection postgres = connection.unwrap(PGConnection.class);
        while (running) {
            PGNotification[] notifications = postgres.getNotifications(POLL_MILLIS);
            if (notifications == null) {
                continue;
            }
            for (PGNotification notification : notifications) {
                handle(connection, notification.getParameter());
            }
        }
    }

    private void handle(Connection connection, String message) throws SQLException {
        if (message.startsWith(PostgresStreamRelay.CLOSE_PREFIX)) {
            delivery.closeStreams(UUID.fromString(message.substring(PostgresStreamRelay.CLOSE_PREFIX.length())));
            return;
        }
        long id = Long.parseLong(message);
        try (PreparedStatement select = connection.prepareStatement(
                "select payload from stream_relay_events where id = ?")) {
            select.setLong(1, id);
            try (ResultSet row = select.executeQuery()) {
                if (row.next()) {
                    List<StreamEnvelope> envelopes = Arrays.asList(
                            mapper.readValue(row.getString(1), StreamEnvelope[].class));
                    delivery.deliver(envelopes);
                }
            }
        }
        lastHandledId = id;
    }

    private void pause(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }
}

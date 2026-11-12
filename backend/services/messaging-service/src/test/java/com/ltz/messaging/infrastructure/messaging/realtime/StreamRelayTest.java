package com.ltz.messaging.infrastructure.messaging.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.ltz.messaging.IntegrationTestSupport;

/**
 * Birden çok örnekte canlı akış (ADR 0001): olaylar PostgreSQL üzerinden tüm örneklere aktarılır. Testte uygulamanın
 * kendisi bir örnek, ayrıca başlatılan {@link StreamRelayListener} ikinci bir örnektir.
 */
class StreamRelayTest extends IntegrationTestSupport {

    @Autowired
    private PostgresStreamRelay relay;

    @Value("${spring.datasource.url}")
    private String url;
    @Value("${spring.datasource.username}")
    private String username;
    @Value("${spring.datasource.password}")
    private String password;

    private final CapturingDelivery secondInstance = new CapturingDelivery();
    private StreamRelayListener listener;

    @BeforeEach
    void startSecondInstance() {
        listener = new StreamRelayListener(url, username, password, secondInstance, Duration.ofMillis(200));
        listener.start();
        await().atMost(Duration.ofSeconds(5)).until(listener::isListening);
    }

    @AfterEach
    void stopSecondInstance() {
        listener.stop();
    }

    @Test
    void ikinciOrnekMesajOlayiniAliciyaGoreAlir() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);

        send(alice, direct, "Örnekler arası merhaba");

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(secondInstance.envelopes)
                .anySatisfy(envelope -> {
                    assertThat(envelope.recipient()).isEqualTo(bob);
                    assertThat(envelope.event()).isEqualTo("message");
                    assertThat(envelope.id()).isEqualTo(direct + ":1");
                    assertThat(envelope.data()).contains("Örnekler arası merhaba").contains("\"readStatus\":null");
                })
                .anySatisfy(envelope -> {
                    assertThat(envelope.recipient()).isEqualTo(alice);
                    assertThat(envelope.data()).contains("\"readStatus\":\"SENT\"");
                }));
    }

    @Test
    void akisKapatmaTumOrneklereGiderTabloyaYazilmaz() {
        UUID user = UUID.randomUUID();
        long rows = rows();

        relay.closeStreams(user);

        await().atMost(Duration.ofSeconds(5)).until(() -> secondInstance.closed.contains(user));
        assertThat(rows()).isEqualTo(rows);
    }

    @Test
    void baglantiKopupGeriGelinceYerelAkislarKapanir() {
        jdbc.queryForList("select pg_terminate_backend(pid) from pg_stat_activity where query like 'LISTEN %'"
                + " and pid <> pg_backend_pid()");

        await().atMost(Duration.ofSeconds(10)).until(() -> secondInstance.closeAll.get() > 0);
        await().atMost(Duration.ofSeconds(5)).until(listener::isListening);
        UUID user = UUID.randomUUID();
        relay.closeStreams(user);
        await().atMost(Duration.ofSeconds(5)).until(() -> secondInstance.closed.contains(user));
    }

    @Test
    void eskiAktarimKayitlariTemizlenir() {
        jdbc.update("insert into stream_relay_events (created_at, payload) values (now() - interval '10 minutes', '[]')");
        jdbc.update("insert into stream_relay_events (payload) values ('[]')");

        relay.deleteExpired();

        assertThat(jdbc.queryForObject("select count(*) from stream_relay_events where created_at < now() - interval "
                + "'5 minutes'", Integer.class)).isZero();
        assertThat(rows()).isPositive();
    }

    private long rows() {
        return jdbc.queryForObject("select count(*) from stream_relay_events", Long.class);
    }

    /** İkinci örneğin akışları yerine geçen kayıt. */
    private static final class CapturingDelivery implements LocalStreamDelivery {

        private final List<StreamEnvelope> envelopes = new CopyOnWriteArrayList<>();
        private final List<UUID> closed = new CopyOnWriteArrayList<>();
        private final AtomicInteger closeAll = new AtomicInteger();

        @Override
        public void deliver(List<StreamEnvelope> batch) {
            envelopes.addAll(batch);
        }

        @Override
        public void closeStreams(UUID userId) {
            closed.add(userId);
        }

        @Override
        public void closeAllLocal() {
            closeAll.incrementAndGet();
        }
    }
}

package com.ltz.messaging.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ltz.messaging.IntegrationTestSupport;
import com.ltz.messaging.application.port.EventDeliveryException;
import com.ltz.messaging.application.port.EventEnvelope;
import com.ltz.messaging.application.port.EventPublisherPort;
import com.ltz.messaging.application.service.OutboxEventStore;
import com.ltz.messaging.application.service.OutboxRelay;
import com.ltz.messaging.infrastructure.client.UnavailableEventPublisher;

/**
 * Outbox teslimi (MSG-16) ve saklama (gerçek PostgreSQL): olay en az bir kez teslim edilir, hata üstel geri çekilmeyle
 * yeniden denenir, en fazla denemede FAILED olur, hazır olmayan yayıncı deneme hakkı yakmaz, alınan olay ikinci kez
 * alınmaz ve saklama süresini aşan olaylar durumdan bağımsız silinir.
 */
class OutboxRelayTest extends IntegrationTestSupport {

    @Autowired
    private OutboxEventStore store;

    @Autowired
    private EventPublisherPort configuredPublisher;

    private final List<EventEnvelope> published = new ArrayList<>();

    @BeforeEach
    void cleanOutbox() {
        jdbc.update("delete from outbox_events");
        published.clear();
    }

    private UUID insertEvent(Instant occurredAt) {
        UUID id = UUID.randomUUID();
        UUID conversation = UUID.randomUUID();
        jdbc.update("""
                insert into outbox_events (id, aggregate_type, aggregate_id, event_type, event_version, payload,
                                           occurred_at, status, attempts, next_attempt_at)
                values (?, 'Conversation', ?, 'MessageSent', 1, cast(? as jsonb), ?, 'PENDING', 0, ?)""",
                id, conversation, "{\"conversationId\":\"%s\",\"seq\":1}".formatted(conversation),
                Timestamp.from(occurredAt), Timestamp.from(occurredAt));
        return id;
    }

    private Map<String, Object> row(UUID id) {
        return jdbc.queryForMap("select status, attempts, next_attempt_at, sent_at from outbox_events where id = ?", id);
    }

    private OutboxRelay relay(Consumer<EventEnvelope> delivery) {
        return new OutboxRelay(store, new EventPublisherPort() {
            @Override
            public void publish(EventEnvelope envelope) {
                delivery.accept(envelope);
            }

            @Override
            public boolean isReady() {
                return true;
            }
        });
    }

    @Test
    void varsayilanHedefYokOlaylarBekler() {
        UUID id = insertEvent(Instant.now());

        assertThat(configuredPublisher).isInstanceOf(UnavailableEventPublisher.class);
        assertThat(new OutboxRelay(store, configuredPublisher).deliverDue()).isZero();

        assertThat(row(id)).containsEntry("status", "PENDING").containsEntry("attempts", 0);
    }

    @Test
    void basariliTeslimSentOlurZarfUreticiyiVeYukuTasir() {
        UUID id = insertEvent(Instant.now().minusSeconds(1));

        int delivered = relay(published::add).deliverDue();

        assertThat(delivered).isEqualTo(1);
        assertThat(published).singleElement().satisfies(envelope -> {
            assertThat(envelope.eventId()).isEqualTo(id);
            assertThat(envelope.eventType()).isEqualTo("MessageSent");
            assertThat(envelope.producer()).isEqualTo("messaging-service");
            assertThat(envelope.payload()).contains("\"seq\"");
        });
        Map<String, Object> row = row(id);
        assertThat(row).containsEntry("status", "SENT");
        assertThat(row.get("sent_at")).isNotNull();
        assertThat(row.get("next_attempt_at")).isNull();
        assertThat(relay(published::add).deliverDue()).isZero();
    }

    @Test
    void hataUstelGeriCekilmeyleYenidenDenenirSonundaFailedOlur() {
        UUID id = insertEvent(Instant.now().minusSeconds(1));
        OutboxRelay failing = relay(envelope -> {
            throw new EventDeliveryException("down");
        });

        Instant before = Instant.now();
        assertThat(failing.deliverDue()).isZero();
        Map<String, Object> first = row(id);
        assertThat(first).containsEntry("status", "PENDING").containsEntry("attempts", 1);
        Instant next = ((Timestamp) first.get("next_attempt_at")).toInstant();
        assertThat(Duration.between(before, next)).isBetween(Duration.ofSeconds(25), Duration.ofSeconds(40));

        for (int attempt = 2; attempt <= 10; attempt++) {
            jdbc.update("update outbox_events set next_attempt_at = now() - interval '1 second' where id = ?", id);
            failing.deliverDue();
        }
        Map<String, Object> last = row(id);
        assertThat(last).containsEntry("status", "FAILED").containsEntry("attempts", 10);
        assertThat(last.get("next_attempt_at")).isNull();
    }

    @Test
    void birOlayinHatasiDigerleriniEngellemez() {
        UUID failingId = insertEvent(Instant.now().minusSeconds(2));
        UUID okId = insertEvent(Instant.now().minusSeconds(1));

        int delivered = relay(envelope -> {
            if (envelope.eventId().equals(failingId)) {
                throw new EventDeliveryException("rejected");
            }
            published.add(envelope);
        }).deliverDue();

        assertThat(delivered).isEqualTo(1);
        assertThat(row(okId)).containsEntry("status", "SENT");
        assertThat(row(failingId)).containsEntry("status", "PENDING").containsEntry("attempts", 1);
    }

    @Test
    void alinanOlayKiraSuresinceIkinciKezAlinmaz() {
        insertEvent(Instant.now().minusSeconds(1));

        assertThat(store.claimDue()).hasSize(1);
        assertThat(store.claimDue()).isEmpty();
    }

    @Test
    void hazirOlmayanYayinciDenemeHakkiYakmaz() {
        UUID id = insertEvent(Instant.now().minusSeconds(1));
        OutboxRelay notReady = new OutboxRelay(store, new UnavailableEventPublisher());

        assertThat(notReady.deliverDue()).isZero();

        assertThat(row(id)).containsEntry("status", "PENDING").containsEntry("attempts", 0);
    }

    @Test
    void saklamaSuresiniAsanOlaylarDurumdanBagimsizSilinir() {
        UUID oldPending = insertEvent(Instant.now().minus(Duration.ofDays(8)));
        UUID oldSent = insertEvent(Instant.now().minus(Duration.ofDays(9)));
        jdbc.update("update outbox_events set status = 'SENT', sent_at = now(), next_attempt_at = null where id = ?",
                oldSent);
        UUID recent = insertEvent(Instant.now().minus(Duration.ofDays(6)));

        assertThat(store.deleteExpired()).isEqualTo(2);

        assertThat(jdbc.queryForList("select id from outbox_events", UUID.class)).containsExactly(recent);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where id in (?, ?)", Integer.class,
                oldPending, oldSent)).isZero();
    }

    @Test
    void gonderilenMesajinOlayiRelayIleTeslimEdilir() throws Exception {
        UUID sender = newUser();
        UUID recipient = newUser();
        UUID conversation = openDirectAndGetId(sender, recipient);
        send(sender, conversation, "merhaba");

        relay(published::add).deliverDue();

        assertThat(published).singleElement().satisfies(envelope -> {
            assertThat(envelope.aggregateId()).isEqualTo(conversation);
            assertThat(envelope.payload()).contains(recipient.toString()).doesNotContain("merhaba");
        });
    }
}

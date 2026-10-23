package com.ltz.social.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import com.jayway.jsonpath.JsonPath;
import com.ltz.social.IntegrationTestSupport;
import com.ltz.social.application.port.EventDeliveryException;
import com.ltz.social.application.port.EventEnvelope;
import com.ltz.social.application.port.SocialEventPublisher;

/** SOC-13: relay en az bir kez teslim eder; başarısızlıkta yeniden dener, sınırda FAILED olur, çift teslim etmez. */
@TestPropertySource(properties = {
        "ltz.social.outbox.adapter=scripted",
        "ltz.social.outbox.max-attempts=3",
        "ltz.social.outbox.batch-size=50"
})
@Import(OutboxRelayTest.ScriptedPublisherConfiguration.class)
class OutboxRelayTest extends IntegrationTestSupport {

    @Autowired
    private OutboxRelay relay;

    @Autowired
    private ScriptedPublisher publisher;

    @BeforeEach
    void cleanOutbox() {
        jdbc.update("delete from outbox_events");
        publisher.reset();
    }

    @Test
    void zamaniGelenOlayTeslimEdilir_SENT_olur_zarfDogruDoldurulur() throws Exception {
        UUID sender = newUser();
        UUID receiver = newUser();
        UUID requestId = sendRequestAndGetId(sender, receiver);

        assertThat(relay.deliverDue()).isEqualTo(1);

        assertThat(status(requestId)).isEqualTo("SENT");
        assertThat(jdbc.queryForObject("select sent_at is not null and next_attempt_at is null from outbox_events "
                + "where aggregate_id = ?", Boolean.class, requestId)).isTrue();
        EventEnvelope envelope = publisher.delivered().getFirst();
        assertThat(envelope.eventType()).isEqualTo("FriendRequestSent");
        assertThat(envelope.eventVersion()).isEqualTo(1);
        assertThat(envelope.producer()).isEqualTo("social-service");
        assertThat(envelope.aggregateId()).isEqualTo(requestId);
        assertThat(envelope.eventId()).isNotNull();
        assertThat(envelope.occurredAt()).isNotNull();
        assertThat(JsonPath.<String>read(envelope.payload(), "$.receiverId")).isEqualTo(receiver.toString());
        // Teslim edilmiş olay bir daha gönderilmez.
        assertThat(relay.deliverDue()).isZero();
        assertThat(publisher.delivered()).hasSize(1);
    }

    @Test
    void basarisizTeslim_denemeSayisiArtar_ileriTarihleYenidenPlanlanir_hemenTekrarDenenmez() throws Exception {
        UUID requestId = sendRequestAndGetId(newUser(), newUser());
        publisher.failing(true);

        assertThat(relay.deliverDue()).isZero();

        assertThat(status(requestId)).isEqualTo("PENDING");
        assertThat(attempts(requestId)).isEqualTo(1);
        assertThat(nextAttemptAt(requestId)).isAfter(Instant.now());
        int calls = publisher.calls();
        assertThat(relay.deliverDue()).isZero();
        assertThat(publisher.calls()).isEqualTo(calls);
    }

    @Test
    void teslimDuzelinceZamaniGelenOlayTeslimEdilir() throws Exception {
        UUID requestId = sendRequestAndGetId(newUser(), newUser());
        publisher.failing(true);
        relay.deliverDue();
        publisher.failing(false);
        makeDue(requestId);

        assertThat(relay.deliverDue()).isEqualTo(1);

        assertThat(status(requestId)).isEqualTo("SENT");
        assertThat(publisher.delivered()).hasSize(1);
    }

    @Test
    void enFazlaDenemedenSonraFAILED_birDahaAlinmaz() throws Exception {
        UUID requestId = sendRequestAndGetId(newUser(), newUser());
        publisher.failing(true);

        for (int attempt = 1; attempt <= 3; attempt++) {
            makeDue(requestId);
            relay.deliverDue();
        }

        assertThat(status(requestId)).isEqualTo("FAILED");
        assertThat(attempts(requestId)).isEqualTo(3);
        int calls = publisher.calls();
        jdbc.update("update outbox_events set next_attempt_at = now() - interval '1 minute' where aggregate_id = ?",
                requestId);
        relay.deliverDue();
        assertThat(publisher.calls()).isEqualTo(calls);
    }

    @Test
    void birOlayinBasarisizligiDigerlerininTesliminiEngellemez() throws Exception {
        UUID first = sendRequestAndGetId(newUser(), newUser());
        UUID second = sendRequestAndGetId(newUser(), newUser());
        publisher.failOn(first);

        assertThat(relay.deliverDue()).isEqualTo(1);

        assertThat(status(first)).isEqualTo("PENDING");
        assertThat(status(second)).isEqualTo("SENT");
    }

    @Test
    void zamaniGelmemisOlayAlinmaz() throws Exception {
        UUID requestId = sendRequestAndGetId(newUser(), newUser());

        // Başka bir relay'in kirası (ya da geri çekilme) sürerken olay alınmamalı.
        jdbc.update("update outbox_events set next_attempt_at = now() + interval '30 seconds' where aggregate_id = ?",
                requestId);

        assertThat(relay.deliverDue()).isZero();
        assertThat(publisher.calls()).isZero();
        assertThat(status(requestId)).isEqualTo("PENDING");
    }

    @Test
    void eszamanliRelaylarHerOlayiYalnizBirKezTeslimEder() throws Exception {
        int events = 12;
        List<UUID> requestIds = new ArrayList<>();
        for (int i = 0; i < events; i++) {
            requestIds.add(sendRequestAndGetId(newUser(), newUser()));
        }

        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Integer>> rounds = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                rounds.add(relay::deliverDue);
            }
            int total = 0;
            for (Future<Integer> future : pool.invokeAll(rounds)) {
                total += future.get();
            }
            assertThat(total).isEqualTo(events);
        } finally {
            pool.shutdownNow();
        }

        assertThat(publisher.delivered()).hasSize(events);
        assertThat(publisher.delivered().stream().map(EventEnvelope::eventId).distinct().count()).isEqualTo(events);
        requestIds.forEach(id -> assertThat(status(id)).isEqualTo("SENT"));
    }

    private String status(UUID aggregateId) {
        return jdbc.queryForObject("select status from outbox_events where aggregate_id = ?", String.class,
                aggregateId);
    }

    private int attempts(UUID aggregateId) {
        return jdbc.queryForObject("select attempts from outbox_events where aggregate_id = ?", Integer.class,
                aggregateId);
    }

    private Instant nextAttemptAt(UUID aggregateId) {
        return jdbc.queryForObject("select next_attempt_at from outbox_events where aggregate_id = ?",
                Timestamp.class, aggregateId).toInstant();
    }

    private void makeDue(UUID aggregateId) {
        jdbc.update("update outbox_events set next_attempt_at = now() - interval '1 second' where aggregate_id = ?",
                aggregateId);
    }

    /** Teslimi kontrollü başarısız kılabilen sahte yayıncı. */
    static class ScriptedPublisher implements SocialEventPublisher {

        private final List<EventEnvelope> delivered = new CopyOnWriteArrayList<>();
        private volatile boolean failing;
        private volatile UUID failingAggregate;
        private volatile int calls;

        @Override
        public synchronized void publish(EventEnvelope envelope) {
            calls++;
            if (failing || envelope.aggregateId().equals(failingAggregate)) {
                throw new EventDeliveryException("scripted failure");
            }
            delivered.add(envelope);
        }

        List<EventEnvelope> delivered() {
            return delivered;
        }

        int calls() {
            return calls;
        }

        void failing(boolean value) {
            failing = value;
        }

        void failOn(UUID aggregateId) {
            failingAggregate = aggregateId;
        }

        void reset() {
            delivered.clear();
            failing = false;
            failingAggregate = null;
            calls = 0;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ScriptedPublisherConfiguration {

        @Bean
        ScriptedPublisher scriptedPublisher() {
            return new ScriptedPublisher();
        }
    }
}

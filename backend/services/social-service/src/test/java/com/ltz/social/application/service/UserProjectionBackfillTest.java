package com.ltz.social.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.jayway.jsonpath.JsonPath;
import com.ltz.social.IntegrationTestSupport;
import com.ltz.social.application.port.EventDeliveryException;
import com.ltz.social.application.port.EventEnvelope;
import com.ltz.social.application.port.SocialEventPublisher;
import com.ltz.social.infrastructure.persistence.repository.FriendshipRepository;
import com.ltz.social.infrastructure.persistence.repository.UserBlockRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * user-service yansıması geçmiş aktarımı: mevcut arkadaşlık ve engeller gerçek zamanlarıyla, doğrudan user-service'e
 * (outbox'a yazmadan) gönderilir; tekrar çalıştırma aynı olayları üretir ve teslim hatasında yarıda kesilir.
 */
class UserProjectionBackfillTest extends IntegrationTestSupport {

    @Autowired
    private FriendshipRepository friendships;

    @Autowired
    private UserBlockRepository blocks;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanRelations() {
        jdbc.update("delete from friendships");
        jdbc.update("delete from user_blocks");
        jdbc.update("delete from outbox_events");
    }

    @Test
    void mevcutArkadaslikVeEngellerGercekZamanlariylaGonderilir() throws Exception {
        UUID a = newUser();
        UUID b = newUser();
        UUID c = newUser();
        befriend(a, b);
        block(a, c).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is2xxSuccessful());
        jdbc.update("delete from outbox_events");
        Instant friendshipAt = createdAt("friendships");
        Instant blockAt = createdAt("user_blocks");
        RecordingPublisher publisher = new RecordingPublisher();

        UserProjectionBackfill.Result result = backfill(publisher, 200).run();

        assertThat(result).isEqualTo(new UserProjectionBackfill.Result(1, 1, true));
        assertThat(publisher.sent).hasSize(2);
        EventEnvelope friendship = publisher.of("FriendshipChanged");
        assertThat(friendship.occurredAt()).isEqualTo(friendshipAt);
        assertThat(friendship.producer()).isEqualTo("social-service");
        assertThat((String) JsonPath.read(friendship.payload(), "$.change")).isEqualTo("ADDED");
        assertThat(List.of((String) JsonPath.read(friendship.payload(), "$.userLowId"),
                (String) JsonPath.read(friendship.payload(), "$.userHighId")))
                .containsExactlyInAnyOrder(a.toString(), b.toString());
        EventEnvelope block = publisher.of("UserBlocked");
        assertThat(block.occurredAt()).isEqualTo(blockAt);
        assertThat((String) JsonPath.read(block.payload(), "$.blockerId")).isEqualTo(a.toString());
        assertThat((String) JsonPath.read(block.payload(), "$.blockedId")).isEqualTo(c.toString());
        // Outbox'a yazılmaz: community gibi diğer tüketiciler aynı engeli yeniden almaz.
        assertThat(jdbc.queryForObject("select count(*) from outbox_events", Integer.class)).isZero();
    }

    @Test
    void tekrarCalistirmaAyniOlayKimlikleriniUretir() throws Exception {
        befriend(newUser(), newUser());
        block(newUser(), newUser());
        RecordingPublisher first = new RecordingPublisher();
        RecordingPublisher second = new RecordingPublisher();

        backfill(first, 200).run();
        backfill(second, 200).run();

        assertThat(second.sent.stream().map(EventEnvelope::eventId).toList())
                .containsExactlyInAnyOrderElementsOf(first.sent.stream().map(EventEnvelope::eventId).toList());
        assertThat(first.sent.stream().map(EventEnvelope::eventId).distinct().count()).isEqualTo(first.sent.size());
    }

    @Test
    void kaldirilanIliskiGonderilmezVeSayfalamaEksiksizdir() throws Exception {
        UUID hub = newUser();
        List<UUID> kept = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            UUID other = newUser();
            befriend(hub, other);
            kept.add(other);
        }
        for (int i = 0; i < 4; i++) {
            block(hub, newUser());
        }
        UUID removed = kept.remove(0);
        jdbc.update("delete from friendships where user_low_id = ? or user_high_id = ?", removed, removed);
        RecordingPublisher publisher = new RecordingPublisher();

        UserProjectionBackfill.Result result = backfill(publisher, 2).run();

        assertThat(result).isEqualTo(new UserProjectionBackfill.Result(4, 4, true));
        assertThat(publisher.sent.stream().filter(event -> event.eventType().equals("FriendshipChanged")).toList())
                .noneMatch(event -> event.payload().contains(removed.toString()));
        assertThat(publisher.sent.stream().map(EventEnvelope::eventId).distinct().count()).isEqualTo(8);
    }

    @Test
    void teslimHatasindaYarimKalirAmaFirlatmazVeTekrarDenenebilir() throws Exception {
        befriend(newUser(), newUser());
        block(newUser(), newUser());
        RecordingPublisher failing = new RecordingPublisher();
        failing.failFrom = 1;

        UserProjectionBackfill.Result interrupted = backfill(failing, 200).run();

        assertThat(interrupted.complete()).isFalse();
        assertThat(interrupted.friendships() + interrupted.blocks()).isEqualTo(1);
        RecordingPublisher healthy = new RecordingPublisher();
        assertThat(backfill(healthy, 200).run()).isEqualTo(new UserProjectionBackfill.Result(1, 1, true));
    }

    @Test
    void servisKimligiYoksaHicbirSeyGonderilmez() throws Exception {
        befriend(newUser(), newUser());
        RecordingPublisher publisher = new RecordingPublisher();
        publisher.ready = false;

        UserProjectionBackfill.Result result = backfill(publisher, 200).run();

        assertThat(result).isEqualTo(new UserProjectionBackfill.Result(0, 0, false));
        assertThat(publisher.sent).isEmpty();
    }

    private UserProjectionBackfill backfill(SocialEventPublisher publisher, int batchSize) {
        return new UserProjectionBackfill(friendships, blocks, publisher, objectMapper, batchSize);
    }

    private Instant createdAt(String table) {
        Timestamp timestamp = jdbc.queryForObject("select created_at from " + table + " limit 1", Timestamp.class);
        return timestamp.toInstant();
    }

    private static final class RecordingPublisher implements SocialEventPublisher {

        final List<EventEnvelope> sent = new ArrayList<>();
        boolean ready = true;
        /** Bu sıradaki (0 tabanlı) gönderimden itibaren teslim başarısız olur; -1 hiç başarısız olmaz. */
        int failFrom = -1;

        @Override
        public void publish(EventEnvelope envelope) {
            if (failFrom >= 0 && sent.size() >= failFrom) {
                throw new EventDeliveryException("user-service answered with HTTP 503");
            }
            sent.add(envelope);
        }

        @Override
        public boolean isReady() {
            return ready;
        }

        EventEnvelope of(String eventType) {
            return sent.stream().filter(event -> event.eventType().equals(eventType)).findFirst().orElseThrow();
        }
    }
}

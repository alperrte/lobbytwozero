package com.ltz.messaging.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;
import com.ltz.messaging.IntegrationTestSupport;
import com.ltz.messaging.application.service.MessageService;

/**
 * MSG-06 metin mesajı gönderme, MSG-10 mesaj bilgileri, yazma yetkisi (MSG-14/15), mükerrer mesaj önleme ve
 * bildirim olayının mesajla aynı transaction'da outbox'a yazılması (MSG-16, MSG-27).
 */
class MessageSendTest extends IntegrationTestSupport {

    @Autowired
    private MessageService messages;

    @Test
    void mesajGondericiZamanVeSirayiTasir201() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);

        sendMessage(alice, direct, "c-1", "Selam! 👋\nAkşam oynuyor muyuz?")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.conversationId").value(direct.toString()))
                .andExpect(jsonPath("$.senderId").value(alice.toString()))
                .andExpect(jsonPath("$.seq").value(1))
                .andExpect(jsonPath("$.type").value("TEXT"))
                .andExpect(jsonPath("$.body").value("Selam! 👋\nAkşam oynuyor muyuz?"))
                .andExpect(jsonPath("$.clientMessageId").value("c-1"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
        sendMessage(bob, direct, "c-1", "Olur").andExpect(status().isCreated())
                .andExpect(jsonPath("$.seq").value(2));
    }

    @Test
    void ayniIstemciKimligiyleYenidenDenemeYeniMesajUretmez200() throws Exception {
        UUID alice = newUser();
        UUID direct = openDirectAndGetId(alice, newUser());
        String first = sendMessage(alice, direct, "retry-1", "Merhaba").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        sendMessage(alice, direct, "retry-1", "Merhaba").andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((String) JsonPath.read(first, "$.id")))
                .andExpect(jsonPath("$.seq").value(1));

        assertThat(messageCount(direct)).isEqualTo(1);
        assertThat(outboxCount(direct)).isEqualTo(1);
    }

    @Test
    void istemciKimligiFarkliIcerikYaDaSohbetteKullanilamaz409() throws Exception {
        UUID alice = newUser();
        UUID direct = openDirectAndGetId(alice, newUser());
        UUID other = openDirectAndGetId(alice, newUser());
        sendMessage(alice, direct, "same-id", "Birinci").andExpect(status().isCreated());

        sendMessage(alice, direct, "same-id", "Başka içerik").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLIENT_MESSAGE_ID_REUSED"));
        sendMessage(alice, other, "same-id", "Birinci").andExpect(status().isConflict());
        assertThat(messageCount(other)).isZero();
    }

    @Test
    void uyeOlmayanVeAyrilanYazamaz404() throws Exception {
        UUID owner = newUser();
        UUID member = newUser();
        UUID group = createGroupAndGetId(owner, member);
        leave(member, group).andExpect(status().isNoContent());

        sendMessage(member, group, "x", "Ben gittim").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONVERSATION_NOT_FOUND"));
        sendMessage(newUser(), group, "y", "Sızma").andExpect(status().isNotFound());
        sendMessage(owner, UUID.randomUUID(), "z", "Yok").andExpect(status().isNotFound());
        assertThat(messageCount(group)).isZero();
    }

    @Test
    void birebirdeEngelVarsaYazilamazKayitVeOlayOlusmaz() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);
        blocks.block(bob, alice);

        sendMessage(alice, direct, "b-1", "Merhaba").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MESSAGING_NOT_ALLOWED"));
        sendMessage(bob, direct, "b-2", "Merhaba").andExpect(status().isForbidden());

        blocks.setUnavailable(true);
        sendMessage(alice, direct, "b-3", "Merhaba").andExpect(status().isServiceUnavailable());

        assertThat(messageCount(direct)).isZero();
        assertThat(outboxCount(direct)).isZero();
    }

    @Test
    void grupMesajiEngelSorgusuYapmazEngelUyeligiEtkilemez() throws Exception {
        UUID owner = newUser();
        UUID member = newUser();
        UUID group = createGroupAndGetId(owner, member);
        blocks.block(owner, member);
        int callsBefore = blocks.calls();

        sendMessage(member, group, "g-1", "Herkese selam").andExpect(status().isCreated());

        assertThat(blocks.calls()).isEqualTo(callsBefore);
    }

    @Test
    void olayIcerikTasimazAlicilarGondericiHaricAktifUyeler() throws Exception {
        UUID owner = newUser();
        UUID stays = newUser();
        UUID leaves = newUser();
        UUID group = createGroupAndGetId(owner, stays, leaves);
        leave(leaves, group).andExpect(status().isNoContent());

        send(owner, group, "Gizli içerik");

        Map<String, Object> event = jdbc.queryForMap("select event_type, event_version, status, payload::text as payload "
                + "from outbox_events where aggregate_id = ?", group);
        assertThat(event).containsEntry("event_type", "MessageSent").containsEntry("event_version", 1)
                .containsEntry("status", "PENDING");
        String payload = (String) event.get("payload");
        assertThat(payload).doesNotContain("Gizli içerik").contains(owner.toString());
        List<String> recipients = JsonPath.read(payload, "$.recipientIds");
        assertThat(recipients).containsExactly(stays.toString());
        assertThat((String) JsonPath.read(payload, "$.conversationType")).isEqualTo("GROUP");
    }

    @Test
    void gonderenKendiMesajiniOkumusSayilir() throws Exception {
        UUID alice = newUser();
        UUID direct = openDirectAndGetId(alice, newUser());

        long seq = send(alice, direct, "Not");

        assertThat(jdbc.queryForObject("select last_read_seq from conversation_members "
                + "where conversation_id = ? and user_id = ?", Long.class, direct, alice)).isEqualTo(seq);
    }

    @Test
    void gecersizMesaj400() throws Exception {
        UUID alice = newUser();
        UUID direct = openDirectAndGetId(alice, newUser());

        for (String body : List.of(
                "{\"clientMessageId\":\"a\",\"body\":\"   \"}",
                "{\"clientMessageId\":\"a\",\"body\":\"%s\"}".formatted("x".repeat(4001)),
                "{\"clientMessageId\":\"a\",\"body\":\"zil\\u0007sesi\"}",
                "{\"clientMessageId\":\"a b\",\"body\":\"Merhaba\"}",
                "{\"clientMessageId\":\"%s\",\"body\":\"Merhaba\"}".formatted("a".repeat(65)),
                "{\"body\":\"Merhaba\"}",
                "{\"clientMessageId\":\"a\"}",
                "{\"clientMessageId\":\"a\",\"body\":\"Merhaba\",\"senderId\":\"%s\"}".formatted(newUser()))) {
            mockMvc.perform(post(MessagingApiPaths.MESSAGES, direct)
                            .header(HttpHeaders.AUTHORIZATION, bearer(alice))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(messageCount(direct)).isZero();
    }

    @Test
    void dortBinKarakterSiniriKabulEdilir() throws Exception {
        UUID alice = newUser();
        UUID direct = openDirectAndGetId(alice, newUser());

        sendMessage(alice, direct, "long", "ğ".repeat(4000)).andExpect(status().isCreated());
    }

    @Test
    void dakikadaOtuzMesajSiniri429() throws Exception {
        UUID alice = newUser();
        UUID direct = openDirectAndGetId(alice, newUser());
        for (int i = 0; i < 30; i++) {
            sendMessage(alice, direct, "r-" + i, "Mesaj " + i).andExpect(status().isCreated());
        }

        sendMessage(alice, direct, "r-30", "Bir fazla").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_MESSAGES"));
        // Önceden kaydedilmiş mesajın yeniden denemesi sınıra takılmaz.
        sendMessage(alice, direct, "r-0", "Mesaj 0").andExpect(status().isOk());
        assertThat(messageCount(direct)).isEqualTo(30);
    }

    @Test
    void eszamanliGonderimlerdeSiraBoslukluVeTekrarliOlmaz() throws Exception {
        UUID owner = newUser();
        UUID[] others = Stream.generate(UUID::randomUUID).limit(7).toArray(UUID[]::new);
        UUID group = createGroupAndGetId(owner, others);
        List<UUID> senders = Stream.concat(Stream.of(owner), Arrays.stream(others)).toList();
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Long>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(senders.size())) {
            for (UUID sender : senders) {
                results.add(pool.submit(() -> {
                    start.await();
                    return messages.send(sender, group, UUID.randomUUID().toString(), "eşzamanlı").message().seq();
                }));
            }
            start.countDown();
            List<Long> seqs = new ArrayList<>();
            for (Future<Long> result : results) {
                seqs.add(result.get());
            }
            assertThat(seqs).containsExactlyInAnyOrderElementsOf(
                    LongStream.rangeClosed(1, senders.size()).boxed().toList());
        }
        assertThat(jdbc.queryForObject("select last_message_seq from conversations where id = ?", Long.class, group))
                .isEqualTo(senders.size());
    }

    private int messageCount(UUID conversationId) {
        return jdbc.queryForObject("select count(*) from messages where conversation_id = ?", Integer.class,
                conversationId);
    }

    private int outboxCount(UUID conversationId) {
        return jdbc.queryForObject("select count(*) from outbox_events where aggregate_id = ?", Integer.class,
                conversationId);
    }
}

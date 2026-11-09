package com.ltz.messaging.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.ltz.messaging.IntegrationTestSupport;

/**
 * MSG-19 mesaja yanıt: yanıt aynı sohbetteki görülebilen, silinmemiş bir mesaja verilir; alıntı önizlemesi okuyana göre
 * hesaplanır (görebiliyorsa içerik, silinmişse yalnız "silindi", göremiyorsa yalnız "görüntülenemiyor"). Menü bilgisi
 * (MSG-24): {@code deletableForEveryoneUntil} gönderen ve grup yöneticisi için dolu.
 */
class MessageReplyTest extends IntegrationTestSupport {

    @Test
    void yanitAlintiOnizlemesiyleDoner() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);
        UUID original = sendAndGetId(alice, direct, "Merhaba");

        sendReply(bob, direct, original, "Selam!")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.replyTo.messageId").value(original.toString()))
                .andExpect(jsonPath("$.replyTo.senderId").value(alice.toString()))
                .andExpect(jsonPath("$.replyTo.type").value("TEXT"))
                .andExpect(jsonPath("$.replyTo.snippet").value("Merhaba"))
                .andExpect(jsonPath("$.replyTo.deleted").value(false))
                .andExpect(jsonPath("$.replyTo.unavailable").value(false));

        history(alice, direct, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].replyTo.messageId").value(original.toString()))
                .andExpect(jsonPath("$.items[0].replyTo.snippet").value("Merhaba"))
                .andExpect(jsonPath("$.items[1].replyTo").doesNotExist());
        listConversations(alice, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].lastMessage.replyTo.snippet").value("Merhaba"));
    }

    @Test
    void uzunGovdeninOnizlemesiYuzBirimdeKesilirVekilBolunmez() throws Exception {
        UUID alice = newUser();
        UUID direct = openDirectAndGetId(alice, newUser());
        String body = "a".repeat(99) + "😀" + "b".repeat(10);
        UUID original = sendAndGetId(alice, direct, body);

        sendReply(alice, direct, original, "uzun").andExpect(status().isCreated())
                .andExpect(jsonPath("$.replyTo.snippet").value("a".repeat(99)));
    }

    @Test
    void baskaSohbetinMesajinaYanitVerilemez() throws Exception {
        UUID alice = newUser();
        UUID direct = openDirectAndGetId(alice, newUser());
        UUID other = openDirectAndGetId(alice, newUser());
        UUID foreign = sendAndGetId(alice, other, "Başka sohbet");
        int before = messageCount(direct);

        sendReply(alice, direct, foreign, "Yanıt").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MESSAGE_NOT_FOUND"));
        assertThat(messageCount(direct)).isEqualTo(before);
    }

    @Test
    void sonradanKatilanEskiMesajaYanitVeremezAlintiyiGoremez() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID carol = newUser();
        UUID group = createGroupAndGetId(alice, bob);
        UUID early = sendAndGetId(alice, group, "Gizli eski konu");
        addMembers(alice, group, carol).andExpect(status().isOk());

        sendReply(carol, group, early, "Ben de").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MESSAGE_NOT_FOUND"));
        sendReply(alice, group, early, "Hatırlatma").andExpect(status().isCreated());

        history(carol, group, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].replyTo.messageId").value(early.toString()))
                .andExpect(jsonPath("$.items[0].replyTo.unavailable").value(true))
                .andExpect(jsonPath("$.items[0].replyTo.snippet").doesNotExist())
                .andExpect(jsonPath("$.items[0].replyTo.senderId").doesNotExist());
        history(bob, group, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].replyTo.snippet").value("Gizli eski konu"));
    }

    @Test
    void herkestenSilinmisMesajaYanitVerilemez() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);
        UUID original = sendAndGetId(alice, direct, "Silinecek");
        jdbc.update("update messages set deleted_at = now(), deleted_by = ?, body = null where id = ?", alice,
                original);
        int before = messageCount(direct);

        sendReply(bob, direct, original, "Geç kaldım").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("MESSAGE_DELETED"));
        assertThat(messageCount(direct)).isEqualTo(before);
    }

    @Test
    void silinmisMesajinAlintisiYalnizSilindiDer() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);
        UUID original = sendAndGetId(alice, direct, "Pişman oldum");
        sendReply(bob, direct, original, "Ne?").andExpect(status().isCreated());
        jdbc.update("update messages set deleted_at = now(), deleted_by = ?, body = null where id = ?", alice,
                original);

        history(bob, direct, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].replyTo.deleted").value(true))
                .andExpect(jsonPath("$.items[0].replyTo.snippet").doesNotExist())
                .andExpect(jsonPath("$.items[1].deleted").value(true))
                .andExpect(jsonPath("$.items[1].body").doesNotExist());
    }

    @Test
    void yanitYenidenDenemesindeFarkliHedefCakisir() throws Exception {
        UUID alice = newUser();
        UUID direct = openDirectAndGetId(alice, newUser());
        UUID first = sendAndGetId(alice, direct, "Bir");
        UUID second = sendAndGetId(alice, direct, "İki");
        String clientMessageId = UUID.randomUUID().toString();

        sendReply(alice, direct, clientMessageId, first, "Yanıt").andExpect(status().isCreated());
        sendReply(alice, direct, clientMessageId, first, "Yanıt").andExpect(status().isOk());
        sendReply(alice, direct, clientMessageId, second, "Yanıt").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLIENT_MESSAGE_ID_REUSED"));
    }

    @Test
    void gonderenVeGrupYoneticisiIcinSilmeHakkiKirkSekizSaatlikSonuDoner() throws Exception {
        UUID owner = newUser();
        UUID admin = newUser();
        UUID member = newUser();
        UUID group = createGroupAndGetId(owner, admin, member);
        changeRole(owner, group, admin, "ADMIN").andExpect(status().isOk());

        String response = sendMessage(member, group, UUID.randomUUID().toString(), "Menü")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Instant createdAt = Instant.parse(JsonPath.read(response, "$.createdAt"));
        Instant until = Instant.parse(JsonPath.read(response, "$.deletableForEveryoneUntil"));
        assertThat(until).isEqualTo(createdAt.plus(Duration.ofHours(48)));

        history(admin, group, "").andExpect(jsonPath("$.items[0].deletableForEveryoneUntil").exists());
        history(owner, group, "").andExpect(jsonPath("$.items[0].deletableForEveryoneUntil").exists());
        UUID other = newUser();
        addMembers(owner, group, other).andExpect(status().isOk());
        send(owner, group, "Son");
        history(other, group, "").andExpect(jsonPath("$.items[0].deletableForEveryoneUntil").doesNotExist());
    }

    @Test
    void birebirdeKarsiTarafinMesajindaSilmeHakkiYok() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);
        send(alice, direct, "Benim");

        history(bob, direct, "").andExpect(jsonPath("$.items[0].deletableForEveryoneUntil").doesNotExist());
        history(alice, direct, "").andExpect(jsonPath("$.items[0].deletableForEveryoneUntil").exists());
    }

    @Test
    void kirkSekizSaatGecenMesajdaSilmeHakkiKalmaz() throws Exception {
        UUID alice = newUser();
        UUID direct = openDirectAndGetId(alice, newUser());
        UUID old = sendAndGetId(alice, direct, "Eski");
        jdbc.update("update messages set created_at = now() - interval '49 hours' where id = ?", old);

        history(alice, direct, "").andExpect(jsonPath("$.items[0].deletableForEveryoneUntil").doesNotExist());
    }

    @Test
    void yanitAkistaAliciyaGoreAlintiTasir() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID carol = newUser();
        UUID group = createGroupAndGetId(alice, bob);
        UUID early = sendAndGetId(alice, group, "Eski konu");
        addMembers(alice, group, carol).andExpect(status().isOk());
        MvcResult bobStream = openStreamFor(bob);
        MvcResult carolStream = openStreamFor(carol);

        sendReply(alice, group, early, "Canlı yanıt").andExpect(status().isCreated());

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(streamContent(bobStream))
                .contains("Canlı yanıt").contains("\"snippet\":\"Eski konu\""));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(streamContent(carolStream))
                .contains("Canlı yanıt").contains("\"unavailable\":true"));
        assertThat(streamContent(carolStream)).doesNotContain("Eski konu");
    }
}

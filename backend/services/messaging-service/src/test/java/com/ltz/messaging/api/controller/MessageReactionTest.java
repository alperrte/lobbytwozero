package com.ltz.messaging.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import com.ltz.messaging.IntegrationTestSupport;

/**
 * MSG-23 mesaj tepkileri: kişi başına mesaj başına tek emoji tepkisi; değiştirilir ve kaldırılır. Özet emoji başına sayı
 * ve isteyenin kendi tepkisidir; liste mesajı görebilen her üyeye açıktır. Silinmiş ya da görülemeyen mesaja tepki
 * verilemez. Değişiklik mesajı görebilen üyelerin akışına {@code reaction} olarak gider.
 */
class MessageReactionTest extends IntegrationTestSupport {

    private static final String HEART = "❤️";
    private static final String THUMBS = "👍";

    @Test
    void tepkiVerilirDegistirilirKaldirilir() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);
        UUID message = sendAndGetId(alice, direct, "Tepki ver");

        react(bob, direct, message, HEART).andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.length()").value(1))
                .andExpect(jsonPath("$.summary[0].emoji").value(HEART))
                .andExpect(jsonPath("$.summary[0].count").value(1))
                .andExpect(jsonPath("$.mine").value(HEART));
        history(alice, direct, "").andExpect(jsonPath("$.items[0].reactions.summary[0].emoji").value(HEART))
                .andExpect(jsonPath("$.items[0].reactions.mine").doesNotExist());

        react(bob, direct, message, THUMBS).andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.length()").value(1))
                .andExpect(jsonPath("$.summary[0].emoji").value(THUMBS))
                .andExpect(jsonPath("$.mine").value(THUMBS));

        unreact(bob, direct, message).andExpect(status().isNoContent());
        unreact(bob, direct, message).andExpect(status().isNoContent());
        history(bob, direct, "").andExpect(jsonPath("$.items[0].reactions.summary.length()").value(0))
                .andExpect(jsonPath("$.items[0].reactions.mine").doesNotExist());
    }

    @Test
    void ozetSayiyaGoreSiralanirKendiTepkimAyridir() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID carol = newUser();
        UUID dave = newUser();
        UUID group = createGroupAndGetId(alice, bob, carol, dave);
        UUID message = sendAndGetId(alice, group, "Oylama");

        react(dave, group, message, HEART).andExpect(status().isOk());
        react(alice, group, message, THUMBS).andExpect(status().isOk());
        react(bob, group, message, THUMBS).andExpect(status().isOk());
        react(carol, group, message, THUMBS).andExpect(status().isOk());

        history(dave, group, "").andExpect(jsonPath("$.items[0].reactions.summary[0].emoji").value(THUMBS))
                .andExpect(jsonPath("$.items[0].reactions.summary[0].count").value(3))
                .andExpect(jsonPath("$.items[0].reactions.summary[1].emoji").value(HEART))
                .andExpect(jsonPath("$.items[0].reactions.mine").value(HEART));
        listConversations(carol, "").andExpect(jsonPath("$.items[0].lastMessage.reactions.mine").value(THUMBS));
    }

    @Test
    void tepkiListesiniGorebilenHerUyeGorur() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID carol = newUser();
        UUID group = createGroupAndGetId(alice, bob, carol);
        UUID message = sendAndGetId(alice, group, "Kim ne dedi");
        react(bob, group, message, HEART).andExpect(status().isOk());
        react(carol, group, message, THUMBS).andExpect(status().isOk());

        reactions(alice, group, message).andExpect(status().isOk())
                .andExpect(jsonPath("$.messageId").value(message.toString()))
                .andExpect(jsonPath("$.reactions.length()").value(2))
                .andExpect(jsonPath("$.reactions[0].userId").value(carol.toString()))
                .andExpect(jsonPath("$.reactions[0].emoji").value(THUMBS))
                .andExpect(jsonPath("$.reactions[0].reactedAt").exists())
                .andExpect(jsonPath("$.reactions[1].userId").value(bob.toString()));
        reactions(newUser(), group, message).andExpect(status().isNotFound());
    }

    @Test
    void gecersizEmoji400() throws Exception {
        UUID alice = newUser();
        UUID direct = openDirectAndGetId(alice, newUser());
        UUID message = sendAndGetId(alice, direct, "Mesaj");

        react(alice, direct, message, "ab").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REACTION"));
        react(alice, direct, message, THUMBS + THUMBS).andExpect(status().isBadRequest());
    }

    @Test
    void dakikadaAltmisSiniri() throws Exception {
        UUID alice = newUser();
        UUID direct = openDirectAndGetId(alice, newUser());
        UUID message = sendAndGetId(alice, direct, "Spam");

        for (int index = 0; index < 60; index++) {
            react(alice, direct, message, index % 2 == 0 ? HEART : THUMBS).andExpect(status().isOk());
        }
        react(alice, direct, message, HEART).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REACTIONS"));
    }

    @Test
    void silinmisMesajaTepkiVerilemezSatirKalmaz() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);
        UUID message = sendAndGetId(alice, direct, "Silinecek");
        react(bob, direct, message, HEART).andExpect(status().isOk());

        deleteMessage(alice, direct, message, "everyone").andExpect(status().isNoContent());

        react(bob, direct, message, THUMBS).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("MESSAGE_DELETED"));
        assertThat(jdbc.queryForObject("select count(*) from message_reactions where message_id = ?", Integer.class,
                message)).isZero();
        reactions(bob, direct, message).andExpect(status().isOk())
                .andExpect(jsonPath("$.reactions.length()").value(0));
    }

    @Test
    void sonradanKatilanVeGizleyenTepkiVeremez() throws Exception {
        UUID owner = newUser();
        UUID bob = newUser();
        UUID late = newUser();
        UUID group = createGroupAndGetId(owner, bob);
        UUID early = sendAndGetId(owner, group, "Eski");
        addMembers(owner, group, late).andExpect(status().isOk());

        react(late, group, early, HEART).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MESSAGE_NOT_FOUND"));
        reactions(late, group, early).andExpect(status().isNotFound());

        deleteMessage(bob, group, early, "me").andExpect(status().isNoContent());
        react(bob, group, early, HEART).andExpect(status().isNotFound());
    }

    @Test
    void tepkiOlayiAliciyaGoreMineTasir() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);
        UUID message = sendAndGetId(alice, direct, "Canlı tepki");
        MvcResult aliceStream = openStreamFor(alice);
        MvcResult bobStream = openStreamFor(bob);

        react(bob, direct, message, HEART).andExpect(status().isOk());

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(streamContent(aliceStream))
                .contains("event:reaction").contains("\"messageId\":\"" + message + "\"")
                .contains("\"emoji\":\"" + HEART + "\"").contains("\"mine\":null"));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(streamContent(bobStream))
                .contains("event:reaction").contains("\"mine\":\"" + HEART + "\""));
    }
}

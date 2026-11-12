package com.ltz.messaging.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.ltz.messaging.IntegrationTestSupport;
import com.ltz.messaging.TestImages;

/**
 * MSG-25 benden sil: mesaj yalnız silen kullanıcının görünümünden (geçmiş, sohbet listesi, okunmamış sayısı, görsel,
 * okuyanlar) kalkar; diğer katılımcılar ve okundu bilgisi etkilenmez. İdempotenttir; değişiklik yalnız silenin kendi
 * akışlarına gider.
 */
class MessageHideTest extends IntegrationTestSupport {

    @Test
    void bendenSilinenMesajYalnizSileninGecmisindenKalkar() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);
        UUID first = sendAndGetId(alice, direct, "Birinci");
        UUID second = sendAndGetId(alice, direct, "İkinci");

        deleteMessage(bob, direct, second, "me").andExpect(status().isNoContent());

        history(bob, direct, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(first.toString()));
        history(alice, direct, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].readStatus").value("SENT"));
    }

    @Test
    void bendenSilmeIdempotentVeKendiMesajindaDaCalisir() throws Exception {
        UUID alice = newUser();
        UUID direct = openDirectAndGetId(alice, newUser());
        UUID own = sendAndGetId(alice, direct, "Kendi mesajım");

        deleteMessage(alice, direct, own, "me").andExpect(status().isNoContent());
        deleteMessage(alice, direct, own, "me").andExpect(status().isNoContent());

        history(alice, direct, "").andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void gizlenenSonMesajYerineOncekiGorunurHepsiGizliyseNull() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);
        UUID first = sendAndGetId(alice, direct, "Bir");
        UUID second = sendAndGetId(alice, direct, "İki");
        UUID third = sendAndGetId(alice, direct, "Üç");

        deleteMessage(bob, direct, third, "me").andExpect(status().isNoContent());
        deleteMessage(bob, direct, second, "me").andExpect(status().isNoContent());
        listConversations(bob, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].lastMessage.id").value(first.toString()));
        listConversations(alice, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].lastMessage.id").value(third.toString()));

        deleteMessage(bob, direct, first, "me").andExpect(status().isNoContent());
        listConversations(bob, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(direct.toString()))
                .andExpect(jsonPath("$.items[0].lastMessage").doesNotExist());
    }

    @Test
    void gizlenenOkunmamisSayilmaz() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);
        send(alice, direct, "Bir");
        UUID second = sendAndGetId(alice, direct, "İki");
        send(alice, direct, "Üç");

        deleteMessage(bob, direct, second, "me").andExpect(status().isNoContent());

        listConversations(bob, "").andExpect(jsonPath("$.items[0].unreadCount").value(2));
    }

    @Test
    void gizlenenGorselVeOkuyanlarGizleyeneKapanir() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);
        UUID attachment = uploadAndGetId(alice, direct, TestImages.png(4, 4), MediaType.IMAGE_PNG_VALUE);
        UUID image = idOf(sendImage(alice, direct, attachment, null).andExpect(status().isCreated()));

        deleteMessage(bob, direct, image, "me").andExpect(status().isNoContent());
        download(bob, direct, attachment).andExpect(status().isNotFound());
        download(alice, direct, attachment).andExpect(status().isOk());

        deleteMessage(alice, direct, image, "me").andExpect(status().isNoContent());
        messageReads(alice, direct, image).andExpect(status().isNotFound());
    }

    @Test
    void gorulemeyenMesajGizlenemezGecersizKapsam400() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);
        UUID other = openDirectAndGetId(alice, newUser());
        UUID foreign = sendAndGetId(alice, other, "Başka sohbet");
        UUID mine = sendAndGetId(alice, direct, "Bu sohbet");

        deleteMessage(alice, direct, foreign, "me").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MESSAGE_NOT_FOUND"));
        deleteMessage(newUser(), direct, mine, "me").andExpect(status().isNotFound());
        deleteMessage(alice, direct, mine, "x").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DELETE_SCOPE"));
    }

    @Test
    void sonradanKatilanEskiMesajiGizleyemez() throws Exception {
        UUID owner = newUser();
        UUID late = newUser();
        UUID group = createGroupAndGetId(owner, newUser());
        UUID early = sendAndGetId(owner, group, "Eski");
        addMembers(owner, group, late).andExpect(status().isOk());

        deleteMessage(late, group, early, "me").andExpect(status().isNotFound());
    }

    @Test
    void gizlemeYalnizSileninAkislarinaGider() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);
        UUID message = sendAndGetId(alice, direct, "Gizlenecek");
        MvcResult bobStream = openStreamFor(bob);
        MvcResult aliceStream = openStreamFor(alice);

        deleteMessage(bob, direct, message, "me").andExpect(status().isNoContent());

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(streamContent(bobStream))
                .contains("event:message_hidden").contains("\"messageId\":\"" + message + "\""));
        send(alice, direct, "Sonraki");
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(streamContent(aliceStream)).contains("Sonraki"));
        assertThat(streamContent(aliceStream)).doesNotContain("message_hidden");
    }
}

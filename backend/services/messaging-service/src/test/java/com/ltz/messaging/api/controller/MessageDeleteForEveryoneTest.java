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
 * MSG-26 herkesten sil: gönderen (grupta OWNER/ADMIN başkasınınkini de) gönderimden sonraki 48 saat içinde siler. Mesaj
 * satırı kalır (sıra, okunmamış ve okundu bilgisi bozulmaz); gövde, görsel, GIF ve tepkiler gerçekten silinir ve hiçbir
 * yoldan dönmez. Silme, mesajı görebilen üyelerin akışına {@code message_deleted} olarak gider.
 */
class MessageDeleteForEveryoneTest extends IntegrationTestSupport {

    @Test
    void herkestenSilinenMesajinIcerigiHicbirYoldanDonmez() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID carol = newUser();
        UUID group = createGroupAndGetId(alice, bob, carol);
        UUID attachment = uploadAndGetId(alice, group, TestImages.png(4, 4), MediaType.IMAGE_PNG_VALUE);
        UUID image = idOf(sendImage(alice, group, attachment, "Gizli açıklama").andExpect(status().isCreated()));
        jdbc.update("insert into message_reactions (message_id, user_id, emoji, reacted_at) values (?, ?, ?, now())",
                image, bob, "❤️");
        sendReply(carol, group, image, "Güzel").andExpect(status().isCreated());
        int blobsBefore = blobCount();

        deleteMessage(alice, group, image, "everyone").andExpect(status().isNoContent());

        String history = history(bob, group, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[1].id").value(image.toString()))
                .andExpect(jsonPath("$.items[1].deleted").value(true))
                .andExpect(jsonPath("$.items[1].body").doesNotExist())
                .andExpect(jsonPath("$.items[1].attachment").doesNotExist())
                .andExpect(jsonPath("$.items[1].gif").doesNotExist())
                .andExpect(jsonPath("$.items[1].reactions.summary.length()").value(0))
                .andExpect(jsonPath("$.items[0].replyTo.deleted").value(true))
                .andExpect(jsonPath("$.items[0].replyTo.snippet").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(history).doesNotContain("Gizli açıklama");
        download(bob, group, attachment).andExpect(status().isNotFound());
        download(alice, group, attachment).andExpect(status().isNotFound());
        assertThat(blobCount()).isEqualTo(blobsBefore - 1);
        assertThat(jdbc.queryForObject("select count(*) from message_reactions where message_id = ?", Integer.class,
                image)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from message_attachments where id = ?", Integer.class,
                attachment)).isZero();
    }

    @Test
    void sonMesajSilinceListedeSilindiGorunur() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);
        UUID message = sendAndGetId(alice, direct, "Son söz");

        deleteMessage(alice, direct, message, "everyone").andExpect(status().isNoContent());

        String list = listConversations(bob, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].lastMessage.id").value(message.toString()))
                .andExpect(jsonPath("$.items[0].lastMessage.deleted").value(true))
                .andReturn().getResponse().getContentAsString();
        assertThat(list).doesNotContain("Son söz");
    }

    @Test
    void gifMesajiSilinceGifAlanlariTemizlenir() throws Exception {
        gifs.publish("abc123", "kedi");
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);
        UUID gif = idOf(sendGif(alice, direct, "abc123", null).andExpect(status().isCreated()));

        deleteMessage(alice, direct, gif, "everyone").andExpect(status().isNoContent());

        history(bob, direct, "").andExpect(jsonPath("$.items[0].deleted").value(true))
                .andExpect(jsonPath("$.items[0].type").value("GIF"))
                .andExpect(jsonPath("$.items[0].gif").doesNotExist());
        assertThat(jdbc.queryForObject("select count(*) from messages where id = ? and gif_id is null "
                + "and gif_url is null and body is null", Integer.class, gif)).isEqualTo(1);
    }

    @Test
    void siraVeOkunmamisBozulmaz() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);
        send(alice, direct, "Bir");
        UUID second = sendAndGetId(alice, direct, "İki");
        listConversations(bob, "").andExpect(jsonPath("$.items[0].unreadCount").value(2));

        deleteMessage(alice, direct, second, "everyone").andExpect(status().isNoContent());

        listConversations(bob, "").andExpect(jsonPath("$.items[0].unreadCount").value(2));
        assertThat(send(alice, direct, "Üç")).isEqualTo(3);
    }

    @Test
    void yalnizGonderenYaDaGrupYoneticisiSilebilir() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);
        UUID directMessage = sendAndGetId(alice, direct, "Birebir");
        deleteMessage(bob, direct, directMessage, "everyone").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_ALLOWED_TO_DELETE"));

        UUID owner = newUser();
        UUID admin = newUser();
        UUID member = newUser();
        UUID other = newUser();
        UUID group = createGroupAndGetId(owner, admin, member, other);
        changeRole(owner, group, admin, "ADMIN").andExpect(status().isOk());
        UUID byMember = sendAndGetId(member, group, "Üye");
        UUID byOther = sendAndGetId(other, group, "Diğer");
        UUID byOwner = sendAndGetId(owner, group, "Sahip");

        deleteMessage(member, group, byOther, "everyone").andExpect(status().isForbidden());
        deleteMessage(admin, group, byMember, "everyone").andExpect(status().isNoContent());
        deleteMessage(owner, group, byOther, "everyone").andExpect(status().isNoContent());
        deleteMessage(admin, group, byOwner, "everyone").andExpect(status().isNoContent());
    }

    @Test
    void kirkSekizSaatSonraSilinemez() throws Exception {
        UUID alice = newUser();
        UUID direct = openDirectAndGetId(alice, newUser());
        UUID old = sendAndGetId(alice, direct, "Eski");
        jdbc.update("update messages set created_at = now() - interval '49 hours' where id = ?", old);

        deleteMessage(alice, direct, old, "everyone").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("DELETE_WINDOW_EXPIRED"));
        history(alice, direct, "").andExpect(jsonPath("$.items[0].deleted").value(false));
    }

    @Test
    void tekrarSilmeIdempotentSilinenMesajaYanitVerilemez() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);
        UUID message = sendAndGetId(alice, direct, "Bir kez");

        deleteMessage(alice, direct, message, "everyone").andExpect(status().isNoContent());
        deleteMessage(alice, direct, message, "everyone").andExpect(status().isNoContent());
        sendReply(bob, direct, message, "Geç").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("MESSAGE_DELETED"));
        deleteMessage(bob, direct, message, "me").andExpect(status().isNoContent());
    }

    @Test
    void gorulemeyenMesajSilinemez() throws Exception {
        UUID owner = newUser();
        UUID late = newUser();
        UUID group = createGroupAndGetId(owner, newUser());
        UUID early = sendAndGetId(owner, group, "Eski");
        addMembers(owner, group, late).andExpect(status().isOk());
        changeRole(owner, group, late, "ADMIN").andExpect(status().isOk());

        deleteMessage(late, group, early, "everyone").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MESSAGE_NOT_FOUND"));
    }

    @Test
    void silmeOlayiGorebilenlereGiderGizleyeneGitmez() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID carol = newUser();
        UUID group = createGroupAndGetId(alice, bob, carol);
        UUID message = sendAndGetId(alice, group, "Yayından kalkacak");
        deleteMessage(carol, group, message, "me").andExpect(status().isNoContent());
        MvcResult bobStream = openStreamFor(bob);
        MvcResult carolStream = openStreamFor(carol);

        deleteMessage(alice, group, message, "everyone").andExpect(status().isNoContent());

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(streamContent(bobStream))
                .contains("event:message_deleted").contains("\"messageId\":\"" + message + "\""));
        send(alice, group, "Sonraki");
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(streamContent(carolStream)).contains("Sonraki"));
        assertThat(streamContent(carolStream)).doesNotContain("message_deleted");
    }

    private int blobCount() {
        return jdbc.queryForObject("select count(*) from attachment_blobs", Integer.class);
    }
}

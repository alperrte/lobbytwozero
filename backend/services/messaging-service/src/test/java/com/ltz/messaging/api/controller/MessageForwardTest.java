package com.ltz.messaging.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;
import com.ltz.messaging.IntegrationTestSupport;
import com.ltz.messaging.TestImages;

/**
 * MSG-20 mesaj iletme: görülebilen, silinmemiş mesaj tek seferde en çok 5 sohbete iletilir; her hedefte iletenin yazma
 * yetkisi ve engel kontrolü uygulanır, biri uygun değilse hiçbiri gönderilmez. Kopya iletenin yeni mesajıdır ("İletildi",
 * 5+ iletimde "Çok kez iletildi"); görsel baytları kopyalanmaz. Aynı istek tekrarlanırsa çift mesaj oluşmaz.
 */
class MessageForwardTest extends IntegrationTestSupport {

    @Test
    void mesajSohbetlereIletildiEtiketiyleIletilir() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID source = openDirectAndGetId(alice, bob);
        UUID first = openDirectAndGetId(bob, newUser());
        UUID second = createGroupAndGetId(bob, newUser());
        UUID original = sendAndGetId(alice, source, "İlet bunu");
        sendReply(alice, source, original, "Yanıtlı").andExpect(status().isCreated());

        String response = forward(bob, source, original, "fw-1", first, second)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[0].conversationId").value(first.toString()))
                .andExpect(jsonPath("$.messages[0].senderId").value(bob.toString()))
                .andExpect(jsonPath("$.messages[0].body").value("İlet bunu"))
                .andExpect(jsonPath("$.messages[0].forwarded").value(true))
                .andExpect(jsonPath("$.messages[0].forwardedManyTimes").value(false))
                .andExpect(jsonPath("$.messages[0].replyTo").doesNotExist())
                .andExpect(jsonPath("$.messages[0].readStatus").value("SENT"))
                .andExpect(jsonPath("$.messages[1].conversationId").value(second.toString()))
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain(alice.toString());

        history(bob, first, "").andExpect(jsonPath("$.items[0].body").value("İlet bunu"))
                .andExpect(jsonPath("$.items[0].forwarded").value(true));
        history(bob, second, "").andExpect(jsonPath("$.items[0].forwarded").value(true));
    }

    @Test
    void besIletimdenSonraCokKezIletildi() throws Exception {
        UUID user = newUser();
        UUID conversation = openDirectAndGetId(user, newUser());
        UUID current = sendAndGetId(user, conversation, "Zincir");

        for (int hop = 1; hop <= 5; hop++) {
            String response = forward(user, conversation, current, "chain-" + hop, conversation)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.messages[0].forwardedManyTimes").value(hop >= 5))
                    .andReturn().getResponse().getContentAsString();
            current = UUID.fromString(JsonPath.read(response, "$.messages[0].id"));
        }
    }

    @Test
    void altiHedefVeTekrarEdenHedef400() throws Exception {
        UUID user = newUser();
        UUID source = openDirectAndGetId(user, newUser());
        UUID message = sendAndGetId(user, source, "Çok hedef");
        UUID[] six = new UUID[6];
        for (int index = 0; index < six.length; index++) {
            six[index] = openDirectAndGetId(user, newUser());
        }

        forward(user, source, message, "fw-six", six).andExpect(status().isBadRequest());
        forward(user, source, message, "fw-none").andExpect(status().isBadRequest());
        forward(user, source, message, "fw-dup", six[0], six[0]).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DUPLICATE_FORWARD_TARGET"));
    }

    @Test
    void hedeflerdenBiriUygunDegilseHicbiriGonderilmez() throws Exception {
        UUID bob = newUser();
        UUID carol = newUser();
        UUID source = openDirectAndGetId(bob, newUser());
        UUID ok = openDirectAndGetId(bob, newUser());
        UUID notMember = openDirectAndGetId(newUser(), newUser());
        UUID blockedDirect = openDirectAndGetId(bob, carol);
        UUID message = sendAndGetId(bob, source, "Kime gider");
        int okBefore = messageCount(ok);

        forward(bob, source, message, "fw-a", ok, notMember).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONVERSATION_NOT_FOUND"));
        assertThat(messageCount(ok)).isEqualTo(okBefore);

        blocks.block(carol, bob);
        forward(bob, source, message, "fw-b", ok, blockedDirect).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MESSAGING_NOT_ALLOWED"));
        assertThat(messageCount(ok)).isEqualTo(okBefore);
        assertThat(messageCount(blockedDirect)).isZero();
    }

    @Test
    void gorselIletilinceBaytKopyalanmaz() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID source = openDirectAndGetId(alice, bob);
        UUID target = openDirectAndGetId(bob, newUser());
        UUID attachment = uploadAndGetId(alice, source, TestImages.png(6, 6), MediaType.IMAGE_PNG_VALUE);
        UUID image = idOf(sendImage(alice, source, attachment, "Foto").andExpect(status().isCreated()));
        int blobs = blobCount();

        String response = forward(bob, source, image, "fw-img", target).andExpect(status().isCreated())
                .andExpect(jsonPath("$.messages[0].type").value("IMAGE"))
                .andExpect(jsonPath("$.messages[0].body").value("Foto"))
                .andReturn().getResponse().getContentAsString();
        UUID copyId = UUID.fromString(JsonPath.read(response, "$.messages[0].id"));
        UUID copyAttachment = UUID.fromString(JsonPath.read(response, "$.messages[0].attachment.id"));
        String copyUrl = JsonPath.read(response, "$.messages[0].attachment.url");
        assertThat(blobCount()).isEqualTo(blobs);
        assertThat(copyAttachment).isNotEqualTo(attachment);
        assertThat(copyUrl).contains(target.toString());
        download(bob, target, copyAttachment).andExpect(status().isOk());
        download(bob, source, copyAttachment).andExpect(status().isNotFound());

        deleteMessage(alice, source, image, "everyone").andExpect(status().isNoContent());
        download(bob, target, copyAttachment).andExpect(status().isOk());
        download(bob, source, attachment).andExpect(status().isNotFound());
        assertThat(blobCount()).isEqualTo(blobs);

        deleteMessage(bob, target, copyId, "everyone").andExpect(status().isNoContent());
        assertThat(blobCount()).isEqualTo(blobs - 1);
    }

    @Test
    void gifIletilinceAnlikKaydiKopyalanir() throws Exception {
        gifs.publish("abc123", "kedi");
        UUID user = newUser();
        UUID source = openDirectAndGetId(user, newUser());
        UUID target = openDirectAndGetId(user, newUser());
        UUID gif = idOf(sendGif(user, source, "abc123", null).andExpect(status().isCreated()));

        forward(user, source, gif, "fw-gif", target).andExpect(status().isCreated())
                .andExpect(jsonPath("$.messages[0].type").value("GIF"))
                .andExpect(jsonPath("$.messages[0].gif.id").value("abc123"));
    }

    @Test
    void iletmeYenidenDenemesiCiftMesajUretmez() throws Exception {
        UUID user = newUser();
        UUID source = openDirectAndGetId(user, newUser());
        UUID target = openDirectAndGetId(user, newUser());
        UUID message = sendAndGetId(user, source, "Bir kez");

        String first = forward(user, source, message, "fw-retry", target).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String second = forward(user, source, message, "fw-retry", target).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> firstIds = JsonPath.read(first, "$.messages[*].id");
        List<String> secondIds = JsonPath.read(second, "$.messages[*].id");
        assertThat(secondIds).isEqualTo(firstIds);
        assertThat(messageCount(target)).isEqualTo(1);
    }

    @Test
    void silinmisYaDaGorulemeyenMesajIletilemez() throws Exception {
        UUID owner = newUser();
        UUID late = newUser();
        UUID group = createGroupAndGetId(owner, newUser());
        UUID target = openDirectAndGetId(late, newUser());
        UUID early = sendAndGetId(owner, group, "Eski");
        addMembers(owner, group, late).andExpect(status().isOk());

        forward(late, group, early, "fw-late", target).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MESSAGE_NOT_FOUND"));

        UUID fresh = sendAndGetId(owner, group, "Yeni");
        deleteMessage(owner, group, fresh, "everyone").andExpect(status().isNoContent());
        forward(late, group, fresh, "fw-del", target).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("MESSAGE_DELETED"));
        assertThat(messageCount(target)).isZero();
    }

    @Test
    void iletmeOranSinirinaSayilir() throws Exception {
        UUID user = newUser();
        UUID source = openDirectAndGetId(user, newUser());
        UUID message = sendAndGetId(user, source, "Sınır");
        UUID[] targets = new UUID[5];
        for (int index = 0; index < targets.length; index++) {
            targets[index] = openDirectAndGetId(user, newUser());
        }
        for (int batch = 0; batch < 5; batch++) {
            forward(user, source, message, "fw-rate-" + batch, targets).andExpect(status().isCreated());
        }
        // Sınır 30/dk: 1 gönderim + 25 iletme = 26; 5 hedefli yeni istek sınırı aşar, hiçbiri yazılmaz.
        forward(user, source, message, "fw-rate-over", targets).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_MESSAGES"));
        assertThat(messageCount(targets[0])).isEqualTo(5);
    }

    @Test
    void iletilenGorselYuklemeOranSinirinaSayilmaz() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID source = openDirectAndGetId(alice, bob);
        UUID attachment = uploadAndGetId(alice, source, TestImages.png(4, 4), MediaType.IMAGE_PNG_VALUE);
        UUID image = idOf(sendImage(alice, source, attachment, null).andExpect(status().isCreated()));
        UUID[] targets = new UUID[5];
        for (int index = 0; index < targets.length; index++) {
            targets[index] = openDirectAndGetId(bob, newUser());
        }
        forward(bob, source, image, "fw-up-1", targets).andExpect(status().isCreated());
        forward(bob, source, image, "fw-up-2", targets).andExpect(status().isCreated());

        upload(bob, source, TestImages.png(4, 4), MediaType.IMAGE_PNG_VALUE).andExpect(status().isCreated());
    }

    private int blobCount() {
        return jdbc.queryForObject("select count(*) from attachment_blobs", Integer.class);
    }
}

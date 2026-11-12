package com.ltz.messaging.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.ltz.messaging.IntegrationTestSupport;
import com.ltz.messaging.TestImages;

/**
 * MSG-17 görsel mesajı ve MSG-18 emoji: yüklenen görsel mesaja bağlanır ve geçmişte/sohbet listesinde görünür; görsel
 * yalnız yükleyenin, aynı sohbette ve bir kez kullanılabilir; bağlanamayan görsel mesajı ve sırayı da geri alır. Emoji
 * (ZWJ dizileri, ten rengi, bayrak) olduğu gibi saklanır; bozuk UTF-16 (eşlenmemiş vekil) reddedilir.
 */
class MediaMessageTest extends IntegrationTestSupport {

    private static final String FAMILY = "👨‍👩‍👧‍👦";
    private static final String THUMBS_SKIN = "👍🏽";
    private static final String FLAG = "🇹🇷";

    @Test
    void gorselMesajiAciklamaylaGonderilirGecmisteVeListedeGorunur() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);
        UUID attachment = uploadAndGetId(alice, direct, TestImages.png(40, 30), MediaType.IMAGE_PNG_VALUE);

        sendImage(alice, direct, attachment, "Bak 😄")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("IMAGE"))
                .andExpect(jsonPath("$.body").value("Bak 😄"))
                .andExpect(jsonPath("$.attachment.id").value(attachment.toString()))
                .andExpect(jsonPath("$.attachment.mimeType").value("image/png"))
                .andExpect(jsonPath("$.attachment.width").value(40))
                .andExpect(jsonPath("$.attachment.url")
                        .value("/api/v1/messaging/conversations/%s/attachments/%s".formatted(direct, attachment)))
                .andExpect(jsonPath("$.gif").doesNotExist());

        history(bob, direct, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].type").value("IMAGE"))
                .andExpect(jsonPath("$.items[0].attachment.id").value(attachment.toString()));
        listConversations(bob, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].lastMessage.type").value("IMAGE"))
                .andExpect(jsonPath("$.items[0].lastMessage.attachment.id").value(attachment.toString()));
    }

    @Test
    void aciklamasizGorselMesajiBodyNullDoner() throws Exception {
        UUID alice = newUser();
        UUID direct = openDirectAndGetId(alice, newUser());
        UUID attachment = uploadAndGetId(alice, direct, TestImages.png(4, 4), MediaType.IMAGE_PNG_VALUE);

        sendImage(alice, direct, attachment, null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").doesNotExist())
                .andExpect(jsonPath("$.attachment.id").value(attachment.toString()));
    }

    @Test
    void gonderilmisBaskasininYaDaBaskaSohbetinGorseliKullanilamazSiraIlerlemez() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);
        UUID other = openDirectAndGetId(alice, newUser());
        UUID sent = uploadAndGetId(alice, direct, TestImages.png(4, 4), MediaType.IMAGE_PNG_VALUE);
        sendImage(alice, direct, sent, null).andExpect(status().isCreated()).andExpect(jsonPath("$.seq").value(1));
        UUID bobs = uploadAndGetId(bob, direct, TestImages.png(4, 4), MediaType.IMAGE_PNG_VALUE);
        UUID elsewhere = uploadAndGetId(alice, other, TestImages.png(4, 4), MediaType.IMAGE_PNG_VALUE);

        sendImage(alice, direct, sent, null).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ATTACHMENT_NOT_AVAILABLE"));
        sendImage(alice, direct, bobs, null).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ATTACHMENT_NOT_AVAILABLE"));
        sendImage(alice, direct, elsewhere, null).andExpect(status().isUnprocessableContent());
        sendImage(alice, direct, UUID.randomUUID(), null).andExpect(status().isUnprocessableContent());

        // Başarısız gönderimler sıra tüketmez, mesaj kaydı bırakmaz.
        assertThat(send(alice, direct, "sonraki")).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from messages where conversation_id = ?", Integer.class,
                direct)).isEqualTo(2);
    }

    @Test
    void gorselMesajiYenidenDenemesiAyniMesajiDonerFarkliGorsel409() throws Exception {
        UUID alice = newUser();
        UUID direct = openDirectAndGetId(alice, newUser());
        UUID attachment = uploadAndGetId(alice, direct, TestImages.png(4, 4), MediaType.IMAGE_PNG_VALUE);
        UUID another = uploadAndGetId(alice, direct, TestImages.png(4, 4), MediaType.IMAGE_PNG_VALUE);
        String json = "{\"clientMessageId\":\"img-1\",\"attachmentId\":\"%s\"}";

        sendJson(alice, direct, json.formatted(attachment)).andExpect(status().isCreated());
        sendJson(alice, direct, json.formatted(attachment)).andExpect(status().isOk())
                .andExpect(jsonPath("$.attachment.id").value(attachment.toString()));
        sendJson(alice, direct, json.formatted(another)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLIENT_MESSAGE_ID_REUSED"));
    }

    @Test
    void icerikKurallari() throws Exception {
        UUID alice = newUser();
        UUID direct = openDirectAndGetId(alice, newUser());
        UUID attachment = uploadAndGetId(alice, direct, TestImages.png(4, 4), MediaType.IMAGE_PNG_VALUE);

        sendJson(alice, direct, "{\"clientMessageId\":\"e-1\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MESSAGE_CONTENT_REQUIRED"));
        sendJson(alice, direct, """
                {"clientMessageId":"e-2","attachmentId":"%s","gif":{"provider":"GIPHY","id":"abc"}}"""
                .formatted(attachment))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONFLICTING_MESSAGE_CONTENT"));
        sendJson(alice, direct, "{\"clientMessageId\":\"e-3\",\"attachmentId\":\"%s\",\"body\":\"   \"}"
                .formatted(attachment)).andExpect(status().isBadRequest());
        sendJson(alice, direct, "{\"clientMessageId\":\"e-4\",\"attachmentId\":\"yok\"}")
                .andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("select count(*) from messages where conversation_id = ?", Integer.class,
                direct)).isZero();
    }

    @Test
    void emojiDizileriOlduguGibiSaklanir() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);
        String body = "Aile " + FAMILY + " tamam " + THUMBS_SKIN + " " + FLAG + " ❤️";

        sendMessage(alice, direct, "emoji-1", body).andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value(body));
        history(bob, direct, "").andExpect(jsonPath("$.items[0].body").value(body));
        assertThat(jdbc.queryForObject("select body from messages where conversation_id = ?", String.class, direct))
                .isEqualTo(body);
    }

    @Test
    void yalnizEmojiMesajiVeUzunlukUtf16BirimiyleSayilir() throws Exception {
        UUID alice = newUser();
        UUID direct = openDirectAndGetId(alice, newUser());
        // 😀 iki UTF-16 birimidir: 2000 emoji = 4000 birim sınırda kabul, bir fazlası reddedilir.
        String atLimit = "😀".repeat(2000);

        sendMessage(alice, direct, "only-emoji", "🎮").andExpect(status().isCreated());
        sendMessage(alice, direct, "limit", atLimit).andExpect(status().isCreated());
        sendMessage(alice, direct, "over", atLimit + "a").andExpect(status().isBadRequest());
    }

    @Test
    void eslenmemisVekilIcerenBozukEmojiReddedilir() throws Exception {
        UUID alice = newUser();
        UUID direct = openDirectAndGetId(alice, newUser());

        sendJson(alice, direct, "{\"clientMessageId\":\"bad-1\",\"body\":\"yarim \\uD83D emoji\"}")
                .andExpect(status().isBadRequest());
        sendJson(alice, direct, "{\"clientMessageId\":\"bad-2\",\"body\":\"ters \\uDE00\\uD83D\"}")
                .andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("select count(*) from messages where conversation_id = ?", Integer.class,
                direct)).isZero();
    }
}

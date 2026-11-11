package com.ltz.messaging.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.ltz.messaging.IntegrationTestSupport;
import com.ltz.messaging.TestAudio;

/**
 * MSG-21 sesli mesaj: tür dosyadan belirlenir ({@code VOICE}), açıklama alınmaz; geçmiş, liste ve akış süreyi ve dalga
 * formunu taşır; yanıt, iletme (bayt kopyalanmaz), herkesten ve benden silme görseldeki gibi çalışır.
 */
class VoiceMessageTest extends IntegrationTestSupport {

    private static final int DURATION = (100 * 960 - 312) * 1000 / 48_000;

    @Test
    void sesliMesajGonderilirGecmisteListedeVeAkistaGorunur() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);
        MvcResult stream = openStreamFor(bob);
        UUID voice = uploadVoice(alice, direct);

        sendAttachment(alice, direct, "v-1", voice, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("VOICE"))
                .andExpect(jsonPath("$.body").isEmpty())
                .andExpect(jsonPath("$.attachment.id").value(voice.toString()))
                .andExpect(jsonPath("$.attachment.mimeType").value("audio/webm"))
                .andExpect(jsonPath("$.attachment.durationMs").value(DURATION))
                .andExpect(jsonPath("$.attachment.waveform.length()").value(64));

        history(bob, direct, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].type").value("VOICE"))
                .andExpect(jsonPath("$.items[0].attachment.durationMs").value(DURATION))
                .andExpect(jsonPath("$.items[0].attachment.waveform[0]").value(42));
        listConversations(bob, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].lastMessage.type").value("VOICE"))
                .andExpect(jsonPath("$.items[0].lastMessage.attachment.durationMs").value(DURATION));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(streamContent(stream))
                .contains("\"type\":\"VOICE\"").contains("\"durationMs\":" + DURATION));
        download(bob, direct, voice).andExpect(status().isOk());
    }

    @Test
    void sesliMesajaAciklamaEklenemez() throws Exception {
        UUID alice = newUser();
        UUID direct = openDirectAndGetId(alice, newUser());
        UUID voice = uploadVoice(alice, direct);

        sendAttachment(alice, direct, "v-1", voice, "açıklama")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VOICE_CAPTION_NOT_ALLOWED"));

        assertThat(messageCount(direct)).isZero();
        assertThat(jdbc.queryForObject("select message_id from message_attachments where id = ?", UUID.class, voice))
                .isNull();
        sendAttachment(alice, direct, "v-2", voice, null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.seq").value(1));
    }

    @Test
    void sesliMesajYenidenDenenirseAyniMesajDoner() throws Exception {
        UUID alice = newUser();
        UUID direct = openDirectAndGetId(alice, newUser());
        UUID voice = uploadVoice(alice, direct);
        UUID first = idOf(sendAttachment(alice, direct, "v-1", voice, null).andExpect(status().isCreated()));

        sendAttachment(alice, direct, "v-1", voice, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(first.toString()))
                .andExpect(jsonPath("$.type").value("VOICE"));
        assertThat(messageCount(direct)).isEqualTo(1);
    }

    @Test
    void sesliMesajKimligiAciklamaylaTekrarlanirsa409() throws Exception {
        UUID alice = newUser();
        UUID direct = openDirectAndGetId(alice, newUser());
        UUID voice = uploadVoice(alice, direct);
        sendAttachment(alice, direct, "v-1", voice, null).andExpect(status().isCreated());

        sendAttachment(alice, direct, "v-1", voice, "açıklama")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLIENT_MESSAGE_ID_REUSED"));
    }

    @Test
    void baskasininSesiGonderilemez() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);
        UUID voice = uploadVoice(alice, direct);

        sendAttachment(bob, direct, "v-1", voice, null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ATTACHMENT_NOT_AVAILABLE"));
        assertThat(messageCount(direct)).isZero();
    }

    @Test
    void sesliMesajaYanitAlintidaVoiceTurunuGosterir() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);
        UUID voiceMessage = idOf(sendAttachment(alice, direct, "v-1", uploadVoice(alice, direct), null)
                .andExpect(status().isCreated()));

        sendReply(bob, direct, voiceMessage, "Dinledim").andExpect(status().isCreated())
                .andExpect(jsonPath("$.replyTo.messageId").value(voiceMessage.toString()))
                .andExpect(jsonPath("$.replyTo.type").value("VOICE"))
                .andExpect(jsonPath("$.replyTo.snippet").isEmpty());
    }

    @Test
    void sesliMesajIletilinceBaytKopyalanmaz() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID source = openDirectAndGetId(alice, bob);
        UUID target = openDirectAndGetId(bob, newUser());
        UUID voiceMessage = idOf(sendAttachment(alice, source, "v-1", uploadVoice(alice, source), null)
                .andExpect(status().isCreated()));
        int blobs = blobCount();

        String response = forward(bob, source, voiceMessage, "fw-voice", target).andExpect(status().isCreated())
                .andExpect(jsonPath("$.messages[0].type").value("VOICE"))
                .andExpect(jsonPath("$.messages[0].forwarded").value(true))
                .andExpect(jsonPath("$.messages[0].attachment.durationMs").value(DURATION))
                .andExpect(jsonPath("$.messages[0].attachment.waveform.length()").value(64))
                .andReturn().getResponse().getContentAsString();
        UUID copy = UUID.fromString(JsonPath.read(response, "$.messages[0].attachment.id"));

        assertThat(blobCount()).isEqualTo(blobs);
        download(bob, target, copy).andExpect(status().isOk());
    }

    @Test
    void herkestenSilinenSesinIcerigiGider() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);
        UUID voice = uploadVoice(alice, direct);
        UUID message = idOf(sendAttachment(alice, direct, "v-1", voice, null).andExpect(status().isCreated()));
        int blobs = blobCount();

        deleteMessage(alice, direct, message, "everyone").andExpect(status().isNoContent());

        history(bob, direct, "").andExpect(jsonPath("$.items[0].deleted").value(true))
                .andExpect(jsonPath("$.items[0].type").value("VOICE"))
                .andExpect(jsonPath("$.items[0].attachment").isEmpty());
        download(bob, direct, voice).andExpect(status().isNotFound());
        assertThat(blobCount()).isEqualTo(blobs - 1);
    }

    @Test
    void benimSildigimSesiIndiremem() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);
        UUID voice = uploadVoice(alice, direct);
        UUID message = idOf(sendAttachment(alice, direct, "v-1", voice, null).andExpect(status().isCreated()));

        deleteMessage(bob, direct, message, "me").andExpect(status().isNoContent());

        download(bob, direct, voice).andExpect(status().isNotFound());
        download(alice, direct, voice).andExpect(status().isOk());
    }

    private UUID uploadVoice(UUID uploader, UUID conversationId) throws Exception {
        return idOf(mockMvc.perform(post(MessagingApiPaths.ATTACHMENTS, conversationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(uploader))
                        .queryParam("waveform", VoiceUploadTest.WAVEFORM)
                        .contentType("audio/ogg")
                        .content(TestAudio.ogg(100)))
                .andExpect(status().isCreated()));
    }

    private ResultActions sendAttachment(UUID sender, UUID conversationId, String clientMessageId, UUID attachment,
                                         String body) throws Exception {
        String bodyField = body == null ? "" : ",\"body\":\"%s\"".formatted(body);
        return sendJson(sender, conversationId, "{\"clientMessageId\":\"%s\",\"attachmentId\":\"%s\"%s}"
                .formatted(clientMessageId, attachment, bodyField));
    }

    private int blobCount() {
        return jdbc.queryForObject("select count(*) from attachment_blobs", Integer.class);
    }
}

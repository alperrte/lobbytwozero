package com.ltz.messaging.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.ltz.messaging.IntegrationTestSupport;
import com.ltz.messaging.TestAudio;
import com.ltz.messaging.TestImages;

/**
 * MSG-21 sesli mesaj yükleme: yalnız Opus (WebM/Ogg); içerik WebM olarak yeniden paketlenir; süre içerikten hesaplanır;
 * dalga formu isteğe bağlıdır ve doğrulanır; oran sınırı görselle ortaktır.
 */
class VoiceUploadTest extends IntegrationTestSupport {

    static final String WAVEFORM = String.join(",", Collections.nCopies(64, "42"));

    @Test
    void oggYuklenirWebmOlarakSaklanir() throws Exception {
        UUID alice = newUser();
        UUID direct = openDirectAndGetId(alice, newUser());

        String response = uploadVoice(alice, direct, TestAudio.ogg(500), "audio/ogg;codecs=opus", WAVEFORM)
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.mimeType").value("audio/webm"))
                .andExpect(jsonPath("$.durationMs").value((500 * 960 - 312) * 1000 / 48_000))
                .andExpect(jsonPath("$.waveform.length()").value(64))
                .andExpect(jsonPath("$.waveform[0]").value(42))
                .andExpect(jsonPath("$.width").isEmpty())
                .andExpect(jsonPath("$.height").isEmpty())
                .andExpect(jsonPath("$.frameCount").isEmpty())
                .andReturn().getResponse().getContentAsString();
        UUID attachment = UUID.fromString(JsonPath.read(response, "$.id"));

        byte[] stored = download(alice, direct, attachment)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "audio/webm"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(stored).startsWith(0x1A, 0x45, 0xDF, 0xA3);
        assertThat((Integer) JsonPath.read(response, "$.sizeBytes")).isEqualTo(stored.length);
    }

    @Test
    void webmYuklenir() throws Exception {
        UUID alice = newUser();
        UUID direct = openDirectAndGetId(alice, newUser());
        byte[] chrome = new TestAudio.Webm(TestAudio.head(1, 312), TestAudio.packets(100)).unknownSizes().build();

        uploadVoice(alice, direct, chrome, "audio/webm;codecs=opus", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mimeType").value("audio/webm"))
                .andExpect(jsonPath("$.durationMs").value((100 * 960 - 312) * 1000 / 48_000));
    }

    @Test
    void dalgaFormuIstegeBaglidir() throws Exception {
        UUID alice = newUser();
        UUID direct = openDirectAndGetId(alice, newUser());

        uploadVoice(alice, direct, TestAudio.ogg(100), "audio/ogg", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.waveform").isEmpty());
    }

    @Test
    void gecersizDalgaFormu400() throws Exception {
        UUID alice = newUser();
        UUID direct = openDirectAndGetId(alice, newUser());

        uploadVoice(alice, direct, TestAudio.ogg(100), "audio/ogg", "1,2,3,4,5,6,7,8,9,10")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WAVEFORM"));
        uploadVoice(alice, direct, TestImages.png(10, 10), MediaType.IMAGE_PNG_VALUE, WAVEFORM)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WAVEFORM"));
        assertThat(jdbc.queryForObject("select count(*) from message_attachments where uploader_id = ?",
                Integer.class, alice)).isZero();
    }

    @Test
    void desteklenmeyenSesTuru415() throws Exception {
        UUID alice = newUser();
        UUID direct = openDirectAndGetId(alice, newUser());

        upload(alice, direct, TestAudio.ogg(100), "audio/mp4").andExpect(status().isUnsupportedMediaType());
        upload(alice, direct, TestAudio.webm(100), "video/webm").andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void uzunBozukYaDaUyusmayanSes422() throws Exception {
        UUID alice = newUser();
        UUID direct = openDirectAndGetId(alice, newUser());

        uploadVoice(alice, direct, TestAudio.ogg(6250), "audio/ogg", null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("AUDIO_DURATION_OUT_OF_RANGE"));
        uploadVoice(alice, direct, new byte[] {'O', 'g', 'g', 'S', 0, 0}, "audio/ogg", null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_AUDIO"));
        uploadVoice(alice, direct, TestAudio.ogg(100), "audio/webm", null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_AUDIO"));
    }

    @Test
    void sesVeGorselAyniOranSiniriniPaylasir() throws Exception {
        UUID alice = newUser();
        UUID direct = openDirectAndGetId(alice, newUser());
        for (int index = 0; index < 5; index++) {
            upload(alice, direct, TestImages.png(10, 10), MediaType.IMAGE_PNG_VALUE).andExpect(status().isCreated());
            uploadVoice(alice, direct, TestAudio.ogg(50), "audio/ogg", null).andExpect(status().isCreated());
        }

        uploadVoice(alice, direct, TestAudio.ogg(50), "audio/ogg", null)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_UPLOADS"));
    }

    @Test
    void uyeOlmayanSesYukleyemez() throws Exception {
        UUID direct = openDirectAndGetId(newUser(), newUser());
        uploadVoice(newUser(), direct, TestAudio.ogg(50), "audio/ogg", null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONVERSATION_NOT_FOUND"));
    }

    private ResultActions uploadVoice(UUID uploader, UUID conversationId, byte[] content, String contentType,
                                      String waveform) throws Exception {
        var request = post(MessagingApiPaths.ATTACHMENTS, conversationId)
                .header(HttpHeaders.AUTHORIZATION, bearer(uploader))
                .contentType(contentType)
                .content(content);
        if (waveform != null) {
            request.queryParam("waveform", waveform);
        }
        return mockMvc.perform(request);
    }
}

package com.ltz.messaging.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

import com.ltz.messaging.IntegrationTestSupport;
import com.ltz.messaging.TestAudio;

/**
 * Ek indirmede bayt aralığı (MSG-21): tarayıcı oynatıcısı (Safari) sesi parça parça ister. Yetki her istekte, aralık
 * hesaplanmadan önce denetlenir; güvenlik başlıkları aralık yanıtında da vardır.
 */
class AttachmentRangeTest extends IntegrationTestSupport {

    @Test
    void aralikIstegine206Doner() throws Exception {
        Voice voice = sentVoice();
        byte[] full = download(voice.bob, voice.direct, voice.attachment).andReturn().getResponse()
                .getContentAsByteArray();

        byte[] part = range(voice.bob, voice.direct, voice.attachment, "bytes=0-99")
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 0-99/" + full.length))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "audio/webm"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; sandbox"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store, private"))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(part).isEqualTo(Arrays.copyOf(full, 100));
    }

    @Test
    void araliksizIstekTamDosyaVeAcceptRangesDoner() throws Exception {
        Voice voice = sentVoice();
        download(voice.bob, voice.direct, voice.attachment)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"));
    }

    @Test
    void gecersizAralik416() throws Exception {
        Voice voice = sentVoice();
        range(voice.bob, voice.direct, voice.attachment, "bytes=99999999-")
                .andExpect(status().isRequestedRangeNotSatisfiable());
    }

    @Test
    void yetkisizAralikIstegi404() throws Exception {
        Voice voice = sentVoice();
        range(newUser(), voice.direct, voice.attachment, "bytes=0-1")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONVERSATION_NOT_FOUND"));

        deleteMessage(voice.bob, voice.direct, voice.message, "me").andExpect(status().isNoContent());
        range(voice.bob, voice.direct, voice.attachment, "bytes=0-1")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ATTACHMENT_NOT_FOUND"));
    }

    private Voice sentVoice() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);
        UUID attachment = idOf(mockMvc.perform(post(MessagingApiPaths.ATTACHMENTS, direct)
                        .header(HttpHeaders.AUTHORIZATION, bearer(alice))
                        .contentType("audio/ogg")
                        .content(TestAudio.ogg(100)))
                .andExpect(status().isCreated()));
        UUID message = idOf(sendJson(alice, direct, "{\"clientMessageId\":\"r-1\",\"attachmentId\":\"%s\"}"
                .formatted(attachment)).andExpect(status().isCreated()));
        return new Voice(bob, direct, attachment, message);
    }

    private ResultActions range(UUID viewer, UUID conversationId, UUID attachmentId, String range) throws Exception {
        return mockMvc.perform(get(MessagingApiPaths.ATTACHMENT, conversationId, attachmentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(viewer))
                .header(HttpHeaders.RANGE, range));
    }

    private record Voice(UUID bob, UUID direct, UUID attachment, UUID message) {
    }
}

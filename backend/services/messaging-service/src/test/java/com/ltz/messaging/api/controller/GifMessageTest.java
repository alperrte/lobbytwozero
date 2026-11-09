package com.ltz.messaging.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.ltz.messaging.IntegrationTestSupport;

/**
 * MSG-22 GIF platformu: arama ve öne çıkanlar kimlik ister, girdi sunucuda doğrulanır; gönderilen GIF'in adresleri
 * istemciden değil platformdan çözülür; platform yoksa 503, bulunamayan GIF 422. Yazma yetkisi (engel) platform
 * çağrısından önce denetlenir.
 */
class GifMessageTest extends IntegrationTestSupport {

    @Test
    void aramaPlatformSonuclariniDoner() throws Exception {
        UUID alice = newUser();
        gifs.publish("abc123", "kedi dans");
        gifs.publish("def456", "köpek");

        search(alice, "q", "kedi", "limit", "5").andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].provider").value("GIPHY"))
                .andExpect(jsonPath("$.items[0].id").value("abc123"))
                .andExpect(jsonPath("$.items[0].url").value("https://media1.giphy.com/media/abc123/200.gif"))
                .andExpect(jsonPath("$.items[0].width").value(267));
        trending(alice).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void aramaGirdisiSunucudaDogrulanir() throws Exception {
        UUID alice = newUser();

        String[][] invalid = {{}, {"q", ""}, {"q", "   "}, {"q", "a".repeat(51)}, {"q", "kedi", "lang", "xx"},
            {"q", "kedi", "offset", "-1"}, {"q", "kedi", "offset", "500"}, {"q", "a\u0007b"}};
        for (String[] params : invalid) {
            search(alice, params).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_GIF_QUERY"));
        }
        search(alice, "q", "kedi", "limit", "51").andExpect(status().isBadRequest());
        // Emoji ile arama: 50 kod noktası sınırı UTF-16 birimiyle değil kod noktasıyla sayılır.
        search(alice, "q", "😺".repeat(50), "lang", "en", "offset", "499", "limit", "50")
                .andExpect(status().isOk());
        assertThat(gifs.calls()).isEqualTo(1);
    }

    @Test
    void kimliksizAramaReddedilir() throws Exception {
        mockMvc.perform(get(MessagingApiPaths.GIF_SEARCH + "?q=kedi")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(MessagingApiPaths.GIF_TRENDING)).andExpect(status().isUnauthorized());
        assertThat(gifs.calls()).isZero();
    }

    @Test
    void platformYoksa503() throws Exception {
        UUID alice = newUser();
        UUID direct = openDirectAndGetId(alice, newUser());
        gifs.publish("abc123", "kedi");
        gifs.setUnavailable(true);

        search(alice, "q", "kedi").andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("GIF_PROVIDER_UNAVAILABLE"));
        trending(alice).andExpect(status().isServiceUnavailable());
        sendGif(alice, direct, "abc123", null).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("GIF_PROVIDER_UNAVAILABLE"));
        assertThat(messageCount(direct)).isZero();
    }

    @Test
    void gifMesajiAdresleriPlatformdanAlirGecmisteGorunur() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);
        gifs.publish("abc123", "kedi");

        sendGif(alice, direct, "abc123", "Hahaha 😂").andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("GIF"))
                .andExpect(jsonPath("$.body").value("Hahaha 😂"))
                .andExpect(jsonPath("$.gif.provider").value("GIPHY"))
                .andExpect(jsonPath("$.gif.id").value("abc123"))
                .andExpect(jsonPath("$.gif.url").value("https://media1.giphy.com/media/abc123/200.gif"))
                .andExpect(jsonPath("$.gif.previewUrl").value("https://media1.giphy.com/media/abc123/100.gif"))
                .andExpect(jsonPath("$.gif.height").value(200))
                .andExpect(jsonPath("$.attachment").doesNotExist());

        history(bob, direct, "").andExpect(jsonPath("$.items[0].type").value("GIF"))
                .andExpect(jsonPath("$.items[0].gif.url").value("https://media1.giphy.com/media/abc123/200.gif"));
        listConversations(bob, "").andExpect(jsonPath("$.items[0].lastMessage.gif.id").value("abc123"));
    }

    @Test
    void istemciAdresGonderemezBilinmeyenGif422() throws Exception {
        UUID alice = newUser();
        UUID direct = openDirectAndGetId(alice, newUser());

        sendJson(alice, direct, """
                {"clientMessageId":"g-1","gif":{"provider":"GIPHY","id":"abc","url":"https://evil.example/x.gif"}}""")
                .andExpect(status().isBadRequest());
        sendJson(alice, direct, "{\"clientMessageId\":\"g-2\",\"gif\":{\"provider\":\"TENOR\",\"id\":\"abc\"}}")
                .andExpect(status().isBadRequest());
        sendJson(alice, direct, "{\"clientMessageId\":\"g-3\",\"gif\":{\"provider\":\"GIPHY\",\"id\":\"../x\"}}")
                .andExpect(status().isBadRequest());
        sendGif(alice, direct, "yok404", null).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("GIF_NOT_AVAILABLE"));
        assertThat(messageCount(direct)).isZero();
    }

    @Test
    void engelliKullaniciyaGifGonderilemezPlatformCagrilmaz() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);
        gifs.publish("abc123", "kedi");
        blocks.block(bob, alice);

        sendGif(alice, direct, "abc123", null).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MESSAGING_NOT_ALLOWED"));
        assertThat(gifs.calls()).isZero();
    }

    @Test
    void gifMesajiYenidenDenemesiPlatformuTekrarCagirmaz() throws Exception {
        UUID alice = newUser();
        UUID direct = openDirectAndGetId(alice, newUser());
        gifs.publish("abc123", "kedi");
        String json = "{\"clientMessageId\":\"gif-1\",\"gif\":{\"provider\":\"GIPHY\",\"id\":\"abc123\"}}";

        sendJson(alice, direct, json).andExpect(status().isCreated());
        sendJson(alice, direct, json).andExpect(status().isOk()).andExpect(jsonPath("$.gif.id").value("abc123"));
        assertThat(gifs.calls()).isEqualTo(1);
    }

    /** {@code params}: ad, değer, ad, değer... (değerler olduğu gibi gider; URL kodlamasını MockMvc yapar). */
    private ResultActions search(UUID user, String... params) throws Exception {
        MockHttpServletRequestBuilder request = get(MessagingApiPaths.GIF_SEARCH)
                .header(HttpHeaders.AUTHORIZATION, bearer(user));
        for (int index = 0; index < params.length; index += 2) {
            request.param(params[index], params[index + 1]);
        }
        return mockMvc.perform(request);
    }

    private ResultActions trending(UUID user) throws Exception {
        return mockMvc.perform(get(MessagingApiPaths.GIF_TRENDING).header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private int messageCount(UUID conversationId) {
        return jdbc.queryForObject("select count(*) from messages where conversation_id = ?", Integer.class,
                conversationId);
    }
}

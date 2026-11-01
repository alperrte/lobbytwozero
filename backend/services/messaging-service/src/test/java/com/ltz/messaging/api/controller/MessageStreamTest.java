package com.ltz.messaging.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.ltz.messaging.IntegrationTestSupport;
import com.ltz.messaging.infrastructure.messaging.realtime.SseMessageHub;

/**
 * MSG-09 gerçek zamanlı akış (SSE): commit edilmiş mesaj, gönderim anındaki aktif üyelerin açık akışlarına iletilir;
 * üye olmayan almaz. Akış kimlik ister ve kullanıcı başına sınırlıdır.
 */
class MessageStreamTest extends IntegrationTestSupport {

    private static final Duration WAIT = Duration.ofSeconds(5);

    @Autowired
    private SseMessageHub hub;

    @Test
    void mesajUyelerinAkisinaIletilirUyeOlmayanAlmaz() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID outsider = newUser();
        UUID direct = openDirectAndGetId(alice, bob);
        MvcResult bobStream = openStream(bob);
        MvcResult aliceStream = openStream(alice);
        MvcResult outsiderStream = openStream(outsider);

        long seq = send(alice, direct, "Canlı mesaj");

        await().atMost(WAIT).untilAsserted(() -> assertThat(content(bobStream))
                .contains("event:message")
                .contains("id:" + direct + ":" + seq)
                .contains("\"body\":\"Canlı mesaj\"")
                .contains("\"senderId\":\"" + alice + "\""));
        // Gönderenin diğer cihazları da alır.
        await().atMost(WAIT).untilAsserted(() -> assertThat(content(aliceStream)).contains("Canlı mesaj"));
        assertThat(content(outsiderStream)).contains("event:ready").doesNotContain("Canlı mesaj");
    }

    @Test
    void cikarilanUyeYeniMesajiAlmaz() throws Exception {
        UUID owner = newUser();
        UUID removed = newUser();
        UUID stays = newUser();
        UUID group = createGroupAndGetId(owner, removed, stays);
        MvcResult removedStream = openStream(removed);
        MvcResult staysStream = openStream(stays);
        removeMember(owner, group, removed).andExpect(status().isNoContent());

        send(owner, group, "Çıkarıldıktan sonra");

        await().atMost(WAIT).untilAsserted(() -> assertThat(content(staysStream)).contains("Çıkarıldıktan sonra"));
        assertThat(content(removedStream)).doesNotContain("Çıkarıldıktan sonra");
    }

    @Test
    void reddedilenMesajAkisaGitmez() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);
        MvcResult bobStream = openStream(bob);
        blocks.block(bob, alice);

        sendMessage(alice, direct, "blocked", "Gitmemeli").andExpect(status().isForbidden());

        assertThat(content(bobStream)).doesNotContain("Gitmemeli");
    }

    @Test
    void akisBasliklariVeKullaniciBasinaSinir() throws Exception {
        UUID user = newUser();
        mockMvc.perform(get(MessagingApiPaths.STREAM).header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(request().asyncStarted())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string("X-Accel-Buffering", "no"));
        for (int i = 1; i < 5; i++) {
            openStream(user);
        }
        assertThat(hub.openStreams(user)).isEqualTo(5);

        mockMvc.perform(get(MessagingApiPaths.STREAM).header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_STREAMS"));
    }

    @Test
    void kimliksizVeGecersizTokenAkisAcamaz() throws Exception {
        mockMvc.perform(get(MessagingApiPaths.STREAM).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized());
        String wrongAudience = accessToken(newUser(), claims -> claims.audience("other-api"));
        mockMvc.perform(get(MessagingApiPaths.STREAM).header(HttpHeaders.AUTHORIZATION, "Bearer " + wrongAudience))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void akisTokenBitimindeKapanacakSekildeAcilir() throws Exception {
        UUID user = newUser();
        String shortLived = accessToken(user, UnaryOperator.identity());

        MvcResult stream = mockMvc.perform(get(MessagingApiPaths.STREAM)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + shortLived))
                .andExpect(request().asyncStarted()).andReturn();

        // Token 300 sn yaşar; akış 30 dk değil token bitişinde (≈300 sn) kapanacak şekilde kurulur.
        assertThat(stream.getRequest().getAsyncContext().getTimeout()).isBetween(290_000L, 300_000L);
    }

    private MvcResult openStream(UUID user) throws Exception {
        MvcResult result = mockMvc.perform(get(MessagingApiPaths.STREAM)
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(request().asyncStarted())
                .andReturn();
        await().atMost(WAIT).untilAsserted(() -> assertThat(content(result)).contains("event:ready"));
        return result;
    }

    /** SSE her zaman UTF-8'dir (tarayıcı da öyle çözer). */
    private static String content(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}

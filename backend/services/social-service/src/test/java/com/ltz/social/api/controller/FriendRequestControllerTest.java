package com.ltz.social.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;
import com.ltz.social.IntegrationTestSupport;

/** SOC-01..06: gönder, kabul, red, iptal, gelen ve giden bekleyen istek listeleri. */
class FriendRequestControllerTest extends IntegrationTestSupport {

    // ---------------------------------------------------------------- SOC-01

    @Test
    void istekGonderilir_201_gonderenTokendenAlinir() throws Exception {
        UUID sender = newUser();
        UUID receiver = newUser();

        sendRequest(sender, receiver)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.senderId").value(sender.toString()))
                .andExpect(jsonPath("$.receiverId").value(receiver.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.respondedAt").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist());
    }

    @Test
    void kendineIstekGonderilemez_400() throws Exception {
        UUID user = newUser();

        sendRequest(user, user)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CANNOT_REQUEST_SELF"));
    }

    @Test
    void ayniKisiyeIkinciAktifIstekGonderilemez_409() throws Exception {
        UUID sender = newUser();
        UUID receiver = newUser();
        sendRequest(sender, receiver).andExpect(status().isCreated());

        sendRequest(sender, receiver)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FRIEND_REQUEST_ALREADY_PENDING"));
    }

    @Test
    void karsiTarafinBekleyenIstegiVarsaYeniIstekGonderilemez_409() throws Exception {
        UUID first = newUser();
        UUID second = newUser();
        sendRequest(first, second).andExpect(status().isCreated());

        sendRequest(second, first)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INCOMING_FRIEND_REQUEST_PENDING"));
    }

    @Test
    void zatenArkadasOlunanKisiyeIstekGonderilemez_409() throws Exception {
        UUID sender = newUser();
        UUID receiver = newUser();
        UUID requestId = sendRequestAndGetId(sender, receiver);
        act(SocialApiPaths.FRIEND_REQUEST_ACCEPT, receiver, requestId).andExpect(status().isOk());

        sendRequest(sender, receiver)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_FRIENDS"));
        sendRequest(receiver, sender)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_FRIENDS"));
    }

    @Test
    void reddedilenVeyaIptalEdilenIstegeRagmenYenidenIstekGonderilebilir() throws Exception {
        UUID sender = newUser();
        UUID receiver = newUser();
        UUID rejected = sendRequestAndGetId(sender, receiver);
        act(SocialApiPaths.FRIEND_REQUEST_REJECT, receiver, rejected).andExpect(status().isOk());

        UUID second = sendRequestAndGetId(sender, receiver);
        act(SocialApiPaths.FRIEND_REQUEST_CANCEL, sender, second).andExpect(status().isOk());

        sendRequest(sender, receiver).andExpect(status().isCreated());
    }

    @Test
    void gonderenKimligiGovdedenAlinmaz_bilinmeyenAlan400() throws Exception {
        UUID real = newUser();
        UUID forged = newUser();
        UUID receiver = newUser();

        mockMvc.perform(post(SocialApiPaths.FRIEND_REQUESTS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(real))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"receiverId":"%s","senderId":"%s"}
                                """.formatted(receiver, forged)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        assertThat(jdbc.queryForObject("select count(*) from friend_requests where sender_id in (?, ?)",
                Integer.class, real, forged)).isZero();
    }

    @Test
    void istemciKimlikBasliklariYokSayilir() throws Exception {
        UUID real = newUser();
        UUID forged = newUser();
        UUID receiver = newUser();

        String body = mockMvc.perform(post(SocialApiPaths.FRIEND_REQUESTS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(real))
                        .header("X-User-Id", forged.toString())
                        .header("X-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"receiverId":"%s"}
                                """.formatted(receiver)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<String>read(body, "$.senderId")).isEqualTo(real.toString());
    }

    @Test
    void gecersizGovde400_alinanDegerGeriYansitilmez() throws Exception {
        UUID sender = newUser();

        mockMvc.perform(post(SocialApiPaths.FRIEND_REQUESTS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(sender))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("receiverId"));

        mockMvc.perform(post(SocialApiPaths.FRIEND_REQUESTS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(sender))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiverId\":\"not-a-uuid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    // ---------------------------------------------------------------- SOC-02

    @Test
    void alici_istegiKabulEder_karsilikliArkadaslikOlusur() throws Exception {
        UUID sender = newUser();
        UUID receiver = newUser();
        UUID requestId = sendRequestAndGetId(sender, receiver);

        act(SocialApiPaths.FRIEND_REQUEST_ACCEPT, receiver, requestId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.respondedAt").isNotEmpty());

        assertThat(requestStatus(requestId)).isEqualTo("ACCEPTED");
        assertThat(areFriends(sender, receiver)).isTrue();
    }

    @Test
    void gonderenKendiIsteginiKabulEdemez_404_arkadaslikOlusmaz() throws Exception {
        UUID sender = newUser();
        UUID receiver = newUser();
        UUID requestId = sendRequestAndGetId(sender, receiver);

        act(SocialApiPaths.FRIEND_REQUEST_ACCEPT, sender, requestId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FRIEND_REQUEST_NOT_FOUND"));

        assertThat(requestStatus(requestId)).isEqualTo("PENDING");
        assertThat(areFriends(sender, receiver)).isFalse();
    }

    @Test
    void ucuncuKisiBaskasininIsteginiYonetemez_idor404() throws Exception {
        UUID sender = newUser();
        UUID receiver = newUser();
        UUID stranger = newUser();
        UUID requestId = sendRequestAndGetId(sender, receiver);

        act(SocialApiPaths.FRIEND_REQUEST_ACCEPT, stranger, requestId).andExpect(status().isNotFound());
        act(SocialApiPaths.FRIEND_REQUEST_REJECT, stranger, requestId).andExpect(status().isNotFound());
        act(SocialApiPaths.FRIEND_REQUEST_CANCEL, stranger, requestId).andExpect(status().isNotFound());

        assertThat(requestStatus(requestId)).isEqualTo("PENDING");
    }

    @Test
    void olmayanIstek404_gecersizKimlik400() throws Exception {
        act(SocialApiPaths.FRIEND_REQUEST_ACCEPT, newUser(), UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FRIEND_REQUEST_NOT_FOUND"));

        mockMvc.perform(post("/api/v1/social/friend-requests/not-a-uuid/accept")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newUser())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void yanitlanmisIstekTekrarYanitlanamaz_409() throws Exception {
        UUID sender = newUser();
        UUID receiver = newUser();
        UUID requestId = sendRequestAndGetId(sender, receiver);
        act(SocialApiPaths.FRIEND_REQUEST_ACCEPT, receiver, requestId).andExpect(status().isOk());

        act(SocialApiPaths.FRIEND_REQUEST_ACCEPT, receiver, requestId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FRIEND_REQUEST_NOT_PENDING"));
        act(SocialApiPaths.FRIEND_REQUEST_REJECT, receiver, requestId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FRIEND_REQUEST_NOT_PENDING"));
        act(SocialApiPaths.FRIEND_REQUEST_CANCEL, sender, requestId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FRIEND_REQUEST_NOT_PENDING"));

        assertThat(requestStatus(requestId)).isEqualTo("ACCEPTED");
        assertThat(areFriends(sender, receiver)).isTrue();
    }

    // ---------------------------------------------------------------- SOC-03

    @Test
    void alici_istegiReddeder_arkadaslikOlusmaz() throws Exception {
        UUID sender = newUser();
        UUID receiver = newUser();
        UUID requestId = sendRequestAndGetId(sender, receiver);

        act(SocialApiPaths.FRIEND_REQUEST_REJECT, receiver, requestId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.respondedAt").isNotEmpty());

        assertThat(areFriends(sender, receiver)).isFalse();
    }

    @Test
    void gonderenIstegiReddedemez_404() throws Exception {
        UUID sender = newUser();
        UUID requestId = sendRequestAndGetId(sender, newUser());

        act(SocialApiPaths.FRIEND_REQUEST_REJECT, sender, requestId).andExpect(status().isNotFound());

        assertThat(requestStatus(requestId)).isEqualTo("PENDING");
    }

    // ---------------------------------------------------------------- SOC-04

    @Test
    void gonderen_bekleyenIstegiIptalEder() throws Exception {
        UUID sender = newUser();
        UUID receiver = newUser();
        UUID requestId = sendRequestAndGetId(sender, receiver);

        act(SocialApiPaths.FRIEND_REQUEST_CANCEL, sender, requestId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(areFriends(sender, receiver)).isFalse();
        act(SocialApiPaths.FRIEND_REQUEST_ACCEPT, receiver, requestId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FRIEND_REQUEST_NOT_PENDING"));
    }

    @Test
    void aliciIstegiIptalEdemez_404() throws Exception {
        UUID receiver = newUser();
        UUID requestId = sendRequestAndGetId(newUser(), receiver);

        act(SocialApiPaths.FRIEND_REQUEST_CANCEL, receiver, requestId).andExpect(status().isNotFound());

        assertThat(requestStatus(requestId)).isEqualTo("PENDING");
    }

    // ---------------------------------------------------------------- SOC-05

    @Test
    void gelenIstekler_yalnizBekleyenVeKullaniciyaAittir() throws Exception {
        UUID me = newUser();
        UUID pendingSender = newUser();
        UUID rejectedSender = newUser();
        UUID cancelledSender = newUser();
        UUID acceptedSender = newUser();
        UUID pending = sendRequestAndGetId(pendingSender, me);
        act(SocialApiPaths.FRIEND_REQUEST_REJECT, me, sendRequestAndGetId(rejectedSender, me));
        act(SocialApiPaths.FRIEND_REQUEST_CANCEL, cancelledSender, sendRequestAndGetId(cancelledSender, me));
        act(SocialApiPaths.FRIEND_REQUEST_ACCEPT, me, sendRequestAndGetId(acceptedSender, me));
        sendRequest(me, newUser()); // giden istek gelenlerde görünmez
        sendRequest(newUser(), newUser()); // başkasının isteği

        list(SocialApiPaths.FRIEND_REQUESTS_INCOMING, me, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(pending.toString()))
                .andExpect(jsonPath("$.items[0].senderId").value(pendingSender.toString()))
                .andExpect(jsonPath("$.items[0].receiverId").value(me.toString()))
                .andExpect(jsonPath("$.items[0].status").value("PENDING"))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void gelenIstekler_yeniden_eskiyeSirali_imecleSayfalanir() throws Exception {
        UUID me = newUser();
        UUID oldest = sendRequestAndGetId(newUser(), me);
        UUID middle = sendRequestAndGetId(newUser(), me);
        UUID newest = sendRequestAndGetId(newUser(), me);

        String firstPage = list(SocialApiPaths.FRIEND_REQUESTS_INCOMING, me, "?limit=2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(newest.toString()))
                .andExpect(jsonPath("$.items[1].id").value(middle.toString()))
                .andExpect(jsonPath("$.nextCursor").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String cursor = JsonPath.read(firstPage, "$.nextCursor");

        list(SocialApiPaths.FRIEND_REQUESTS_INCOMING, me, "?limit=2&cursor=" + cursor)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(oldest.toString()))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    // ---------------------------------------------------------------- SOC-06

    @Test
    void gonderilenIstekler_yalnizBekleyenVeKullaniciyaAittir() throws Exception {
        UUID me = newUser();
        UUID pendingReceiver = newUser();
        UUID pending = sendRequestAndGetId(me, pendingReceiver);
        UUID cancelled = sendRequestAndGetId(me, newUser());
        act(SocialApiPaths.FRIEND_REQUEST_CANCEL, me, cancelled);
        UUID rejectingReceiver = newUser();
        act(SocialApiPaths.FRIEND_REQUEST_REJECT, rejectingReceiver, sendRequestAndGetId(me, rejectingReceiver))
                .andExpect(status().isOk());
        sendRequest(newUser(), me); // gelen istek gönderilenlerde görünmez

        list(SocialApiPaths.FRIEND_REQUESTS_OUTGOING, me, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(pending.toString()))
                .andExpect(jsonPath("$.items[0].senderId").value(me.toString()))
                .andExpect(jsonPath("$.items[0].receiverId").value(pendingReceiver.toString()))
                .andExpect(jsonPath("$.items[0].status").value("PENDING"));
    }

    @Test
    void gonderilenIstekler_imecleSayfalanir() throws Exception {
        UUID me = newUser();
        UUID oldest = sendRequestAndGetId(me, newUser());
        UUID newest = sendRequestAndGetId(me, newUser());

        String firstPage = list(SocialApiPaths.FRIEND_REQUESTS_OUTGOING, me, "?limit=1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(newest.toString()))
                .andExpect(jsonPath("$.nextCursor").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String cursor = JsonPath.read(firstPage, "$.nextCursor");

        list(SocialApiPaths.FRIEND_REQUESTS_OUTGOING, me, "?limit=1&cursor=" + cursor)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(oldest.toString()))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void bosListe_200() throws Exception {
        list(SocialApiPaths.FRIEND_REQUESTS_INCOMING, newUser(), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void sayfalamaSinirlariSunucudaZorunlu_400() throws Exception {
        UUID me = newUser();

        for (String query : new String[] {"?limit=0", "?limit=-1", "?limit=51", "?limit=abc"}) {
            list(SocialApiPaths.FRIEND_REQUESTS_INCOMING, me, query).andExpect(status().isBadRequest());
        }
        list(SocialApiPaths.FRIEND_REQUESTS_INCOMING, me, "?limit=51")
                .andExpect(jsonPath("$.code").value("INVALID_PAGE_SIZE"));
        list(SocialApiPaths.FRIEND_REQUESTS_OUTGOING, me, "?cursor=bozuk")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
        list(SocialApiPaths.FRIEND_REQUESTS_OUTGOING, me, "?cursor=" + "A".repeat(500))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
    }
}

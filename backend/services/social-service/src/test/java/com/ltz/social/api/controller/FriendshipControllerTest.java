package com.ltz.social.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.ltz.social.IntegrationTestSupport;

/** SOC-07 arkadaş listesi ve SOC-08 arkadaşlıktan çıkarma. */
class FriendshipControllerTest extends IntegrationTestSupport {

    // ---------------------------------------------------------------- SOC-07

    @Test
    void arkadasiOlmayanKullanicininListesiBos_200() throws Exception {
        list(SocialApiPaths.FRIENDS, newUser(), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void liste_yalnizKullanicininArkadaslari_yeniden_eskiyeSirali() throws Exception {
        UUID me = newUser();
        UUID first = newUser();
        UUID second = newUser();
        befriend(me, first);
        befriend(second, me); // yön fark etmez
        befriend(first, newUser()); // başkasının arkadaşlığı listede görünmez

        list(SocialApiPaths.FRIENDS, me, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].friendId").value(second.toString()))
                .andExpect(jsonPath("$.items[1].friendId").value(first.toString()))
                .andExpect(jsonPath("$.items[0].since").isNotEmpty())
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void arkadaslikIkiTarafaDaGorunur_ucuncuKisiGormez() throws Exception {
        UUID first = newUser();
        UUID second = newUser();
        befriend(first, second);

        list(SocialApiPaths.FRIENDS, first, "").andExpect(jsonPath("$.items[0].friendId").value(second.toString()));
        list(SocialApiPaths.FRIENDS, second, "").andExpect(jsonPath("$.items[0].friendId").value(first.toString()));
        list(SocialApiPaths.FRIENDS, newUser(), "").andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void bekleyenIstekReddedilenVeIptalEdilenIstekArkadaslikSayilmaz() throws Exception {
        UUID me = newUser();
        sendRequest(me, newUser()).andExpect(status().isCreated());
        UUID rejected = newUser();
        act(SocialApiPaths.FRIEND_REQUEST_REJECT, rejected, sendRequestAndGetId(me, rejected))
                .andExpect(status().isOk());
        act(SocialApiPaths.FRIEND_REQUEST_CANCEL, me, sendRequestAndGetId(me, newUser())).andExpect(status().isOk());

        list(SocialApiPaths.FRIENDS, me, "").andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void liste_imecleSayfalanir() throws Exception {
        UUID me = newUser();
        UUID oldest = newUser();
        UUID middle = newUser();
        UUID newest = newUser();
        befriend(me, oldest);
        befriend(me, middle);
        befriend(me, newest);

        String firstPage = list(SocialApiPaths.FRIENDS, me, "?limit=2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].friendId").value(newest.toString()))
                .andExpect(jsonPath("$.items[1].friendId").value(middle.toString()))
                .andExpect(jsonPath("$.nextCursor").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String cursor = JsonPath.read(firstPage, "$.nextCursor");

        list(SocialApiPaths.FRIENDS, me, "?limit=2&cursor=" + cursor)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].friendId").value(oldest.toString()))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void sayfalamaSinirlariSunucudaZorunlu_400() throws Exception {
        UUID me = newUser();

        list(SocialApiPaths.FRIENDS, me, "?limit=0").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAGE_SIZE"));
        list(SocialApiPaths.FRIENDS, me, "?limit=51").andExpect(status().isBadRequest());
        list(SocialApiPaths.FRIENDS, me, "?cursor=bozuk").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
    }

    // ---------------------------------------------------------------- SOC-08

    @Test
    void arkadaslikBitirilir_204_iki_tarafinListesindenDuser() throws Exception {
        UUID me = newUser();
        UUID friend = newUser();
        befriend(me, friend);

        unfriend(me, friend).andExpect(status().isNoContent());

        assertThat(areFriends(me, friend)).isFalse();
        list(SocialApiPaths.FRIENDS, me, "").andExpect(jsonPath("$.items.length()").value(0));
        list(SocialApiPaths.FRIENDS, friend, "").andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void arkadaslikIkiTarafindanDaBitirilebilir() throws Exception {
        UUID requester = newUser();
        UUID accepter = newUser();
        befriend(requester, accepter);

        unfriend(accepter, requester).andExpect(status().isNoContent());

        assertThat(areFriends(requester, accepter)).isFalse();
    }

    @Test
    void arkadasOlunmayanKisiyiCikarmak404() throws Exception {
        UUID me = newUser();

        unfriend(me, newUser()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FRIENDSHIP_NOT_FOUND"));
        unfriend(me, me).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FRIENDSHIP_NOT_FOUND"));
    }

    @Test
    void ikinciCikarma404_islemTekrarlanamaz() throws Exception {
        UUID me = newUser();
        UUID friend = newUser();
        befriend(me, friend);
        unfriend(me, friend).andExpect(status().isNoContent());

        unfriend(me, friend).andExpect(status().isNotFound());
        unfriend(friend, me).andExpect(status().isNotFound());
    }

    @Test
    void baskalarininArkadasligiCikarilamaz_idor() throws Exception {
        UUID owner = newUser();
        UUID friend = newUser();
        UUID attacker = newUser();
        befriend(owner, friend);

        // Saldırgan kendi kimliğiyle yalnız kendi arkadaşlıklarını bitirebilir; owner–friend çifti etkilenmez.
        unfriend(attacker, friend).andExpect(status().isNotFound());
        unfriend(attacker, owner).andExpect(status().isNotFound());

        assertThat(areFriends(owner, friend)).isTrue();
    }

    @Test
    void cikarmadanSonraYenidenIstekGonderilebilir() throws Exception {
        UUID me = newUser();
        UUID friend = newUser();
        befriend(me, friend);
        unfriend(me, friend).andExpect(status().isNoContent());

        sendRequest(me, friend).andExpect(status().isCreated());
        sendRequest(friend, me).andExpect(status().isConflict());
    }

    @Test
    void gecersizFriendId400() throws Exception {
        mockMvc.perform(delete("/api/v1/social/friends/not-a-uuid")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newUser())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void eszamanliIkiCikarma_yalnizBiriBasarili() throws Exception {
        UUID me = newUser();
        UUID friend = newUser();
        befriend(me, friend);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Integer>> calls = List.of(
                    () -> unfriend(me, friend).andReturn().getResponse().getStatus(),
                    () -> unfriend(friend, me).andReturn().getResponse().getStatus());
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : pool.invokeAll(calls)) {
                statuses.add(future.get());
            }
            assertThat(statuses).containsExactlyInAnyOrder(204, 404);
        } finally {
            pool.shutdownNow();
        }
        assertThat(areFriends(me, friend)).isFalse();
    }

    // ---------------------------------------------------------------- SOC-13 olayları

    @Test
    void arkadaslikKurulunca_vecikarilincaOlayYazilir() throws Exception {
        UUID sender = newUser();
        UUID receiver = newUser();
        UUID requestId = befriend(sender, receiver);

        assertThat(eventTypes(requestId)).containsExactly("FriendRequestSent", "FriendRequestAccepted");
        String added = jdbc.queryForObject("select payload::text from outbox_events where event_type = "
                + "'FriendshipChanged' and payload ->> 'change' = 'ADDED' and (payload ->> 'userLowId' in (?, ?))",
                String.class, sender.toString(), receiver.toString());
        assertThat(added).contains("userLowId").contains("userHighId");

        unfriend(sender, receiver).andExpect(status().isNoContent());
        Integer removed = jdbc.queryForObject("select count(*) from outbox_events where event_type = "
                + "'FriendshipChanged' and payload ->> 'change' = 'REMOVED' and payload ->> 'userLowId' in (?, ?)",
                Integer.class, sender.toString(), receiver.toString());
        assertThat(removed).isEqualTo(1);
    }

    @Test
    void basarisizCikarma_olayYazmaz() throws Exception {
        UUID me = newUser();
        UUID stranger = newUser();

        unfriend(me, stranger).andExpect(status().isNotFound());

        Integer events = jdbc.queryForObject("select count(*) from outbox_events where payload ->> 'userLowId' in (?, ?)",
                Integer.class, me.toString(), stranger.toString());
        assertThat(events).isZero();
    }

    private ResultActions unfriend(UUID user, UUID friend) throws Exception {
        return mockMvc.perform(delete(SocialApiPaths.FRIEND, friend)
                .header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }
}

package com.ltz.social.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.ltz.social.IntegrationTestSupport;

/** SOC-09 engelleme, SOC-10 engel kaldırma, SOC-11 engellenenler listesi ve engelin diğer akışlara etkisi. */
class BlockControllerTest extends IntegrationTestSupport {

    // ---------------------------------------------------------------- SOC-09

    @Test
    void engellenir_201_engelleyenTokendenAlinir_listedeGorunur() throws Exception {
        UUID me = newUser();
        UUID other = newUser();

        block(me, other).andExpect(status().isCreated())
                .andExpect(jsonPath("$.blockedUserId").value(other.toString()))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        assertThat(isBlocked(me, other)).isTrue();
        assertThat(isBlocked(other, me)).isFalse();
    }

    @Test
    void ikinciEngelIdempotent_200_tekKayitTekOlay() throws Exception {
        UUID me = newUser();
        UUID other = newUser();
        block(me, other).andExpect(status().isCreated());

        block(me, other).andExpect(status().isOk()).andExpect(jsonPath("$.blockedUserId").value(other.toString()));

        assertThat(jdbc.queryForObject("select count(*) from user_blocks where blocker_id = ?", Integer.class, me))
                .isEqualTo(1);
        assertThat(blockedEventCount(me, other)).isEqualTo(1);
    }

    @Test
    void kendiniEngelleme400() throws Exception {
        UUID me = newUser();

        block(me, me).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CANNOT_BLOCK_SELF"));

        assertThat(jdbc.queryForObject("select count(*) from user_blocks where blocker_id = ?", Integer.class, me))
                .isZero();
    }

    @Test
    void engelleyenKimligiGovdedenAlinmaz_bilinmeyenAlan400() throws Exception {
        UUID real = newUser();
        UUID forged = newUser();
        UUID target = newUser();

        mockMvc.perform(post(SocialApiPaths.BLOCKS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(real))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"blockedUserId":"%s","blockerId":"%s"}
                                """.formatted(target, forged)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        mockMvc.perform(post(SocialApiPaths.BLOCKS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(real))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertThat(isBlocked(forged, target)).isFalse();
    }

    @Test
    void engelYaziliOlaydirVeYukYalnizKimlikTasir() throws Exception {
        UUID me = newUser();
        UUID other = newUser();

        block(me, other).andExpect(status().isCreated());

        String payload = jdbc.queryForObject("select payload::text from outbox_events where event_type = "
                + "'UserBlocked' and payload ->> 'blockerId' = ? and payload ->> 'blockedId' = ?", String.class,
                me.toString(), other.toString());
        assertThat(JsonPath.<Map<String, Object>>read(payload, "$")).containsOnlyKeys("blockerId",
                "blockedId");
    }

    // ---------------------------------------------------------------- engelin etkisi

    @Test
    void engelArkadasligiBitirir_ikiTarafinListesindenDuser_olayYazilir() throws Exception {
        UUID me = newUser();
        UUID friend = newUser();
        befriend(me, friend);

        block(me, friend).andExpect(status().isCreated());

        assertThat(areFriends(me, friend)).isFalse();
        list(SocialApiPaths.FRIENDS, me, "").andExpect(jsonPath("$.items.length()").value(0));
        list(SocialApiPaths.FRIENDS, friend, "").andExpect(jsonPath("$.items.length()").value(0));
        Integer removed = jdbc.queryForObject("select count(*) from outbox_events where event_type = "
                + "'FriendshipChanged' and payload ->> 'change' = 'REMOVED' and payload ->> 'userLowId' in (?, ?)",
                Integer.class, me.toString(), friend.toString());
        assertThat(removed).isEqualTo(1);
    }

    @Test
    void engelleyeninGonderdigiBekleyenIstekIptal_engellenenden_gelenRedEdilir() throws Exception {
        UUID blocker = newUser();
        UUID blockedA = newUser();
        UUID blockedB = newUser();
        UUID sentByBlocker = sendRequestAndGetId(blocker, blockedA);
        UUID sentToBlocker = sendRequestAndGetId(blockedB, blocker);

        block(blocker, blockedA).andExpect(status().isCreated());
        block(blocker, blockedB).andExpect(status().isCreated());

        assertThat(requestStatus(sentByBlocker)).isEqualTo("CANCELLED");
        assertThat(requestStatus(sentToBlocker)).isEqualTo("REJECTED");
        list(SocialApiPaths.FRIEND_REQUESTS_OUTGOING, blocker, "").andExpect(jsonPath("$.items.length()").value(0));
        list(SocialApiPaths.FRIEND_REQUESTS_INCOMING, blocker, "").andExpect(jsonPath("$.items.length()").value(0));
        list(SocialApiPaths.FRIEND_REQUESTS_INCOMING, blockedA, "").andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void engelBaskasininIsteklerineDokunmaz() throws Exception {
        UUID me = newUser();
        UUID blocked = newUser();
        UUID unrelated = newUser();
        UUID unrelatedRequest = sendRequestAndGetId(unrelated, newUser());
        UUID withOther = sendRequestAndGetId(me, unrelated);

        block(me, blocked).andExpect(status().isCreated());

        assertThat(requestStatus(unrelatedRequest)).isEqualTo("PENDING");
        assertThat(requestStatus(withOther)).isEqualTo("PENDING");
    }

    @Test
    void engellenenKullanici_engelleyeneIstekGonderemez_403_engelSizdirilmaz() throws Exception {
        UUID blocker = newUser();
        UUID blocked = newUser();
        block(blocker, blocked).andExpect(status().isCreated());

        sendRequest(blocked, blocker).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FRIEND_REQUEST_NOT_ALLOWED"));

        assertThat(pendingBetween(blocker, blocked)).isZero();
    }

    @Test
    void engelleyenEngelledigineIstekGonderemez_409_onceEngeliKaldirmali() throws Exception {
        UUID blocker = newUser();
        UUID blocked = newUser();
        block(blocker, blocked).andExpect(status().isCreated());

        sendRequest(blocker, blocked).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_BLOCKED"));
    }

    @Test
    void davetKoduAkisindaDaEngelGecerli_sayacArtmaz() throws Exception {
        UUID owner = newUser();
        UUID blockedByOwner = newUser();
        UUID blockedOwner = newUser();
        String ownerCode = codeOf(owner);
        String blockedOwnerCode = codeOf(blockedOwner);
        block(owner, blockedByOwner).andExpect(status().isCreated());
        block(owner, blockedOwner).andExpect(status().isCreated());

        // Kod sahibinin engellediği kişi onun koduyla istek başlatamaz.
        redeem(blockedByOwner, ownerCode).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FRIEND_REQUEST_NOT_ALLOWED"));
        // Engelleyen, engellediği kişinin koduyla da istek başlatamaz.
        redeem(owner, blockedOwnerCode).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_BLOCKED"));

        assertThat(jdbc.queryForObject("select used_count from friend_invite_codes where code = ?", Integer.class,
                ownerCode)).isZero();
        assertThat(jdbc.queryForObject("select used_count from friend_invite_codes where code = ?", Integer.class,
                blockedOwnerCode)).isZero();
    }

    @Test
    void engelKalkinca_yenidenIstekGonderilebilir_ancakEskiArkadaslikGeriGelmez() throws Exception {
        UUID me = newUser();
        UUID other = newUser();
        befriend(me, other);
        block(me, other).andExpect(status().isCreated());

        unblock(me, other).andExpect(status().isNoContent());

        assertThat(areFriends(me, other)).isFalse();
        sendRequest(me, other).andExpect(status().isCreated());
    }

    @Test
    void karsilikliEngel_biriKalkincaDigeriSurer() throws Exception {
        UUID first = newUser();
        UUID second = newUser();
        block(first, second).andExpect(status().isCreated());
        block(second, first).andExpect(status().isCreated());

        unblock(first, second).andExpect(status().isNoContent());

        // second hâlâ first'i engelli tutuyor.
        sendRequest(first, second).andExpect(status().isForbidden());
        sendRequest(second, first).andExpect(status().isConflict());

        unblock(second, first).andExpect(status().isNoContent());
        sendRequest(first, second).andExpect(status().isCreated());
    }

    // ---------------------------------------------------------------- SOC-10

    @Test
    void engelKaldirilir_204_listedenDuser_olayYazilir() throws Exception {
        UUID me = newUser();
        UUID other = newUser();
        block(me, other).andExpect(status().isCreated());

        unblock(me, other).andExpect(status().isNoContent());

        assertThat(isBlocked(me, other)).isFalse();
        list(SocialApiPaths.BLOCKS, me, "").andExpect(jsonPath("$.items.length()").value(0));
        Integer unblocked = jdbc.queryForObject("select count(*) from outbox_events where event_type = "
                + "'UserUnblocked' and payload ->> 'blockerId' = ? and payload ->> 'blockedId' = ?", Integer.class,
                me.toString(), other.toString());
        assertThat(unblocked).isEqualTo(1);
    }

    @Test
    void engelYoksa404_ikinciKaldirma404_baskasininEngeliKaldirilamaz() throws Exception {
        UUID me = newUser();
        UUID other = newUser();
        UUID attacker = newUser();
        block(me, other).andExpect(status().isCreated());

        unblock(attacker, other).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BLOCK_NOT_FOUND"));
        // Engellenen kişi, kendisini engelleyenin engelini kaldıramaz (yön önemlidir).
        unblock(other, me).andExpect(status().isNotFound());
        assertThat(isBlocked(me, other)).isTrue();

        unblock(me, other).andExpect(status().isNoContent());
        unblock(me, other).andExpect(status().isNotFound());
    }

    @Test
    void engelKaldirmaSonrasiYenidenEngellemeYeniKayitOlur_201() throws Exception {
        UUID me = newUser();
        UUID other = newUser();
        block(me, other).andExpect(status().isCreated());
        unblock(me, other).andExpect(status().isNoContent());

        block(me, other).andExpect(status().isCreated());

        assertThat(isBlocked(me, other)).isTrue();
    }

    @Test
    void gecersizKimlik400() throws Exception {
        mockMvc.perform(delete("/api/v1/social/blocks/not-a-uuid")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newUser())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    // ---------------------------------------------------------------- SOC-11

    @Test
    void engellenenlerListesi_yalnizKendiEngelleri_yeniden_eskiyeSirali() throws Exception {
        UUID me = newUser();
        UUID first = newUser();
        UUID second = newUser();
        block(me, first).andExpect(status().isCreated());
        block(me, second).andExpect(status().isCreated());
        block(newUser(), first).andExpect(status().isCreated()); // başkasının engeli görünmez

        list(SocialApiPaths.BLOCKS, me, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].blockedUserId").value(second.toString()))
                .andExpect(jsonPath("$.items[1].blockedUserId").value(first.toString()))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
        // Engellenen kişi kendisini kimin engellediğini listeden göremez.
        list(SocialApiPaths.BLOCKS, first, "").andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void engellenenlerListesiBos_200() throws Exception {
        list(SocialApiPaths.BLOCKS, newUser(), "").andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void engellenenlerListesi_imecleSayfalanir_sinirlarZorunlu() throws Exception {
        UUID me = newUser();
        UUID oldest = newUser();
        UUID middle = newUser();
        UUID newest = newUser();
        block(me, oldest).andExpect(status().isCreated());
        block(me, middle).andExpect(status().isCreated());
        block(me, newest).andExpect(status().isCreated());

        String firstPage = list(SocialApiPaths.BLOCKS, me, "?limit=2")
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].blockedUserId").value(newest.toString()))
                .andExpect(jsonPath("$.nextCursor").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String cursor = JsonPath.read(firstPage, "$.nextCursor");
        list(SocialApiPaths.BLOCKS, me, "?limit=2&cursor=" + cursor)
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].blockedUserId").value(oldest.toString()))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());

        list(SocialApiPaths.BLOCKS, me, "?limit=0").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAGE_SIZE"));
        list(SocialApiPaths.BLOCKS, me, "?cursor=bozuk").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
    }

    @Test
    void kimliksizIstekler401() throws Exception {
        mockMvc.perform(get(SocialApiPaths.BLOCKS)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(SocialApiPaths.BLOCKS).contentType(MediaType.APPLICATION_JSON)
                .content("{\"blockedUserId\":\"%s\"}".formatted(UUID.randomUUID()))).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(SocialApiPaths.BLOCK, UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- yardımcılar

    private int blockedEventCount(UUID blocker, UUID blocked) {
        return jdbc.queryForObject("select count(*) from outbox_events where event_type = 'UserBlocked' and "
                + "payload ->> 'blockerId' = ? and payload ->> 'blockedId' = ?", Integer.class, blocker.toString(),
                blocked.toString());
    }

    private String codeOf(UUID owner) throws Exception {
        return JsonPath.read(mockMvc.perform(post(SocialApiPaths.INVITE_CODE)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))).andReturn().getResponse().getContentAsString(),
                "$.code");
    }

    private ResultActions redeem(UUID user, String code) throws Exception {
        return mockMvc.perform(post(SocialApiPaths.FRIEND_REQUESTS_BY_CODE)
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"%s\"}".formatted(code)));
    }
}

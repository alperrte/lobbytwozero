package com.ltz.messaging.api.controller;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;
import com.ltz.messaging.IntegrationTestSupport;

/** MSG-08 kullanıcıya özel sohbet listesi ve MSG-13 sohbet bazlı okunmamış sayısı (okuma işaretçisiyle). */
class ConversationListTest extends IntegrationTestSupport {

    @Test
    void yalnizAktifSohbetlerSonEtkinliktenEskiyeListelenir() throws Exception {
        UUID me = newUser();
        UUID peer = newUser();
        UUID direct = openDirectAndGetId(me, peer);
        UUID group = createGroupAndGetId(me, newUser());
        UUID leftGroup = createGroupAndGetId(newUser(), me);
        leave(me, leftGroup).andExpect(status().isNoContent());
        createGroupAndGetId(newUser(), newUser());
        send(peer, direct, "En yeni");

        listConversations(me, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(direct.toString()))
                .andExpect(jsonPath("$.items[0].type").value("DIRECT"))
                .andExpect(jsonPath("$.items[0].peerUserId").value(peer.toString()))
                .andExpect(jsonPath("$.items[0].memberCount").value(2))
                .andExpect(jsonPath("$.items[0].lastMessage.body").value("En yeni"))
                .andExpect(jsonPath("$.items[0].lastMessage.senderId").value(peer.toString()))
                .andExpect(jsonPath("$.items[0].unreadCount").value(1))
                .andExpect(jsonPath("$.items[1].id").value(group.toString()))
                .andExpect(jsonPath("$.items[1].title").value("Takım"))
                .andExpect(jsonPath("$.items[1].peerUserId").doesNotExist())
                .andExpect(jsonPath("$.items[1].myRole").value("OWNER"))
                .andExpect(jsonPath("$.items[1].lastMessage").doesNotExist())
                .andExpect(jsonPath("$.items[1].unreadCount").value(0))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void imlecleSayfalanirTekrarVeAtlamaYok() throws Exception {
        UUID me = newUser();
        UUID first = openDirectAndGetId(me, newUser());
        UUID second = openDirectAndGetId(me, newUser());
        UUID third = openDirectAndGetId(me, newUser());

        String page = listConversations(me, "?limit=2").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(third.toString()))
                .andExpect(jsonPath("$.items[1].id").value(second.toString()))
                .andReturn().getResponse().getContentAsString();
        String cursor = JsonPath.read(page, "$.nextCursor");

        listConversations(me, "?limit=2&cursor=" + cursor).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(first.toString()))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
        listConversations(me, "?cursor=bozuk!").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
        listConversations(me, "?limit=51").andExpect(status().isBadRequest());
        listConversations(newUser(), "").andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
    }

    /** Tek okuma işaretçisi: mesaj gönderen, sohbeti o mesaja kadar okumuş sayılır (cevap yazan okumuştur). */
    @Test
    void gonderenOkumusSayilirKatilmadanOncekilerSayilmaz() throws Exception {
        UUID owner = newUser();
        UUID member = newUser();
        UUID late = newUser();
        UUID group = createGroupAndGetId(owner, member);
        send(owner, group, "1");
        send(member, group, "2");
        addMembers(owner, group, late).andExpect(status().isOk());
        send(owner, group, "3");

        // owner: 3'ü gönderince 1-3 okunmuş; member: 2'yi gönderince 1-2 okunmuş, 3 okunmamış; late: yalnız 3'ü görür.
        listConversations(owner, "").andExpect(jsonPath("$.items[0].unreadCount").value(0));
        listConversations(member, "").andExpect(jsonPath("$.items[0].unreadCount").value(1));
        listConversations(late, "").andExpect(jsonPath("$.items[0].unreadCount").value(1));
    }

    @Test
    void sonMesajKullanicininGoremedigiyseGosterilmez() throws Exception {
        UUID owner = newUser();
        UUID late = newUser();
        UUID group = createGroupAndGetId(owner, newUser());
        send(owner, group, "Eski sır");
        addMembers(owner, group, late).andExpect(status().isOk());

        listConversations(late, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(group.toString()))
                .andExpect(jsonPath("$.items[0].lastMessage").doesNotExist())
                .andExpect(jsonPath("$.items[0].unreadCount").value(0));
    }

    @Test
    void okumaIsaretcisiIlerlerGeriGitmezSonMesajiAsmaz() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);
        for (int i = 1; i <= 4; i++) {
            send(alice, direct, "Mesaj " + i);
        }

        markRead(bob, direct, 2).andExpect(status().isOk())
                .andExpect(jsonPath("$.lastReadSeq").value(2))
                .andExpect(jsonPath("$.unreadCount").value(2));
        markRead(bob, direct, 1).andExpect(status().isOk())
                .andExpect(jsonPath("$.lastReadSeq").value(2))
                .andExpect(jsonPath("$.unreadCount").value(2));
        markRead(bob, direct, 999).andExpect(status().isOk())
                .andExpect(jsonPath("$.lastReadSeq").value(4))
                .andExpect(jsonPath("$.unreadCount").value(0));
        listConversations(bob, "").andExpect(jsonPath("$.items[0].unreadCount").value(0));

        send(alice, direct, "Mesaj 5");
        listConversations(bob, "").andExpect(jsonPath("$.items[0].unreadCount").value(1));
    }

    @Test
    void okumaIsaretiYalnizUyeIcinGecersizDeger400() throws Exception {
        UUID alice = newUser();
        UUID direct = openDirectAndGetId(alice, newUser());

        markRead(newUser(), direct, 1).andExpect(status().isNotFound());
        markRead(alice, direct, -1).andExpect(status().isBadRequest());
    }
}

package com.ltz.messaging.api.controller;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ltz.messaging.IntegrationTestSupport;

/**
 * MSG-07 sayfalı mesaj geçmişi ve MSG-09 yeniden bağlanınca kayıpsız eşitleme ({@code after}). Yalnız aktif üye görür
 * ve yalnız katıldıktan sonraki mesajları görür (kullanıcı kararı, 2026-11-01).
 */
class MessageHistoryTest extends IntegrationTestSupport {

    @Test
    void geriyeDogruSayfalarYenidenEskiye() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);
        for (int i = 1; i <= 5; i++) {
            send(i % 2 == 0 ? bob : alice, direct, "Mesaj " + i);
        }

        history(bob, direct, "?limit=2").andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].seq").value(5))
                .andExpect(jsonPath("$.items[1].seq").value(4))
                .andExpect(jsonPath("$.hasMore").value(true));
        history(bob, direct, "?limit=2&before=4").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].seq").value(3))
                .andExpect(jsonPath("$.items[1].seq").value(2))
                .andExpect(jsonPath("$.hasMore").value(true));
        history(bob, direct, "?limit=2&before=2").andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].body").value("Mesaj 1"))
                .andExpect(jsonPath("$.items[0].senderId").value(alice.toString()))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void afterIleKacirilanlarEskidenYeniyeGelir() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);
        for (int i = 1; i <= 4; i++) {
            send(alice, direct, "Mesaj " + i);
        }

        history(bob, direct, "?after=1&limit=2").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].seq").value(2))
                .andExpect(jsonPath("$.items[1].seq").value(3))
                .andExpect(jsonPath("$.hasMore").value(true));
        history(bob, direct, "?after=4").andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void sonradanEklenenUyeOncekiMesajlariGormez() throws Exception {
        UUID owner = newUser();
        UUID early = newUser();
        UUID late = newUser();
        UUID group = createGroupAndGetId(owner, early);
        send(owner, group, "Eski 1");
        send(early, group, "Eski 2");
        addMembers(owner, group, late).andExpect(status().isOk());
        send(owner, group, "Yeni");

        history(late, group, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].body").value("Yeni"));
        history(late, group, "?after=0").andExpect(jsonPath("$.items.length()").value(1));
        history(early, group, "").andExpect(jsonPath("$.items.length()").value(3));
    }

    @Test
    void cikarilipGeriEklenenAradakiMesajlariGormez() throws Exception {
        UUID owner = newUser();
        UUID member = newUser();
        UUID group = createGroupAndGetId(owner, member);
        send(owner, group, "Birlikteyken");
        removeMember(owner, group, member).andExpect(status().isNoContent());
        send(owner, group, "Yokken");

        history(member, group, "").andExpect(status().isNotFound());

        addMembers(owner, group, member).andExpect(status().isOk());
        send(owner, group, "Döndükten sonra");
        history(member, group, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].body").value("Döndükten sonra"));
    }

    @Test
    void uyeOlmayan404GecersizParametre400() throws Exception {
        UUID alice = newUser();
        UUID direct = openDirectAndGetId(alice, newUser());

        history(newUser(), direct, "").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONVERSATION_NOT_FOUND"));
        history(alice, direct, "?before=5&after=1").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONFLICTING_PAGE_DIRECTION"));
        history(alice, direct, "?limit=0").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAGE_SIZE"));
        history(alice, direct, "?limit=101").andExpect(status().isBadRequest());
        history(alice, direct, "?before=abc").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        history(alice, direct, "?limit=100").andExpect(status().isOk());
    }
}

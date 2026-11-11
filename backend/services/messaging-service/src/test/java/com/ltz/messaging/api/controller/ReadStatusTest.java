package com.ltz.messaging.api.controller;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ltz.messaging.IntegrationTestSupport;

/**
 * MSG-11 okundu durumu: yalnız isteyenin kendi mesajlarında {@code readStatus} dolu olur (geçmiş, sohbet listesi,
 * gönderim yanıtı); başkasının mesajında {@code null}'dır. Durum su seviyesi formülüyle hesaplanır (spec §3.2).
 */
class ReadStatusTest extends IntegrationTestSupport {

    @Test
    void birebirdeOkununcaReadOlur() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);
        long seq = send(alice, direct, "Selam");

        history(alice, direct, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].readStatus").value("SENT"));
        history(bob, direct, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].readStatus").doesNotExist());

        markRead(bob, direct, seq).andExpect(status().isOk());

        history(alice, direct, "").andExpect(jsonPath("$.items[0].readStatus").value("READ"));
        history(bob, direct, "").andExpect(jsonPath("$.items[0].readStatus").doesNotExist());
    }

    @Test
    void grupKismiOkumaSentKalir() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID carol = newUser();
        UUID group = createGroupAndGetId(alice, bob, carol);
        long seq = send(alice, group, "Herkese");

        markRead(bob, group, seq).andExpect(status().isOk());
        history(alice, group, "").andExpect(jsonPath("$.items[0].readStatus").value("SENT"));

        markRead(carol, group, seq).andExpect(status().isOk());
        history(alice, group, "").andExpect(jsonPath("$.items[0].readStatus").value("READ"));
    }

    @Test
    void ayrilanUyeHesaptanDuser() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID carol = newUser();
        UUID group = createGroupAndGetId(alice, bob, carol);
        long seq = send(alice, group, "Herkese");
        markRead(bob, group, seq).andExpect(status().isOk());
        history(alice, group, "").andExpect(jsonPath("$.items[0].readStatus").value("SENT"));

        leave(carol, group).andExpect(status().isNoContent());

        history(alice, group, "").andExpect(jsonPath("$.items[0].readStatus").value("READ"));
    }

    @Test
    void tekBasinaKalanGonderenSent() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID group = createGroupAndGetId(alice, bob);
        leave(bob, group).andExpect(status().isNoContent());

        send(alice, group, "Kimse yok");

        history(alice, group, "").andExpect(jsonPath("$.items[0].readStatus").value("SENT"));
        listConversations(alice, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].lastMessage.readStatus").value("SENT"));
    }

    @Test
    void listedeYalnizKendiSonMesajimdaDurumVar() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);
        send(alice, direct, "Benimki");

        listConversations(alice, "").andExpect(jsonPath("$.items[0].lastMessage.readStatus").value("SENT"));
        listConversations(bob, "").andExpect(jsonPath("$.items[0].lastMessage.readStatus").doesNotExist());

        long seq = send(bob, direct, "Senin");

        // Son mesaj Bob'un: Alice'in listesinde durum yok, Bob'un listesinde Alice okuyana dek SENT.
        listConversations(alice, "").andExpect(jsonPath("$.items[0].lastMessage.seq").value(seq))
                .andExpect(jsonPath("$.items[0].lastMessage.readStatus").doesNotExist());
        listConversations(bob, "").andExpect(jsonPath("$.items[0].lastMessage.readStatus").value("SENT"));

        markRead(alice, direct, seq).andExpect(status().isOk());

        listConversations(bob, "").andExpect(jsonPath("$.items[0].lastMessage.readStatus").value("READ"));
    }

    @Test
    void gonderimYanitiSentDoner() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);

        sendMessage(alice, direct, "c-1", "Selam").andExpect(status().isCreated())
                .andExpect(jsonPath("$.readStatus").value("SENT"));
        sendMessage(alice, direct, "c-1", "Selam").andExpect(status().isOk())
                .andExpect(jsonPath("$.readStatus").value("SENT"));

        markRead(bob, direct, 1).andExpect(status().isOk());

        sendMessage(alice, direct, "c-1", "Selam").andExpect(status().isOk())
                .andExpect(jsonPath("$.readStatus").value("READ"));
    }

    @Test
    void uyariliUcDurumOkunduGorunur() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID group = createGroupAndGetId(alice, bob);
        send(alice, group, "Eski mesaj");
        leave(bob, group).andExpect(status().isNoContent());

        addMembers(alice, group, newUser()).andExpect(status().isOk());

        // Mesajın tek uygun okuyucusu ayrıldı; yerine gelen sonradan katıldığı için formül READ gösterir (spec §3.2).
        history(alice, group, "").andExpect(jsonPath("$.items[0].readStatus").value("READ"));
    }
}

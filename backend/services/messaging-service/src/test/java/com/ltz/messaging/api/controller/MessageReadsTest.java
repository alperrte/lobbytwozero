package com.ltz.messaging.api.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ltz.messaging.IntegrationTestSupport;

/**
 * MSG-12 mesaj bilgisi ucu: yalnız gönderen, uygun okuyucuları (spec §3.1) okuma saatiyle ve okumayanlarla görür.
 * Erişilemeyen sohbet, başka sohbetin ya da görünmeyen mesaj 404; üye ama gönderen değilse 403 döner.
 */
class MessageReadsTest extends IntegrationTestSupport {

    @Test
    void okuyanlarSaatleOkumayanlarKatilmaSirasiyla() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID carol = newUser();
        UUID dave = newUser();
        UUID group = createGroupAndGetId(alice, bob, carol, dave);
        long seq = send(alice, group, "Herkese");
        markRead(bob, group, seq).andExpect(status().isOk());
        markRead(carol, group, seq).andExpect(status().isOk());
        // Saatler ardışık okumaya bırakılmaz; sıralamayı kanıtlamak için açıkça ayrıştırılır.
        setReadAt(group, bob, Instant.parse("2026-01-01T10:00:00Z"));
        setReadAt(group, carol, Instant.parse("2026-01-01T10:05:00Z"));

        messageReads(alice, group, messageId(group, seq)).andExpect(status().isOk())
                .andExpect(jsonPath("$.seq").value(seq))
                .andExpect(jsonPath("$.readStatus").value("SENT"))
                .andExpect(jsonPath("$.readers[*].userId").value(contains(carol.toString(), bob.toString())))
                .andExpect(jsonPath("$.readers[0].readAt").value("2026-01-01T10:05:00Z"))
                .andExpect(jsonPath("$.readers[1].readAt").value("2026-01-01T10:00:00Z"))
                .andExpect(jsonPath("$.pending", hasSize(1)))
                .andExpect(jsonPath("$.pending[0].userId").value(dave.toString()));
    }

    @Test
    void sonradanKatilanVeYenidenEklenenListedeYok() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID carol = newUser();
        UUID erin = newUser();
        UUID group = createGroupAndGetId(alice, bob, carol);
        long seq = send(alice, group, "Eski mesaj");
        addMembers(alice, group, erin).andExpect(status().isOk());
        leave(bob, group).andExpect(status().isNoContent());
        addMembers(alice, group, bob).andExpect(status().isOk());

        messageReads(alice, group, messageId(group, seq)).andExpect(status().isOk())
                .andExpect(jsonPath("$.readers", empty()))
                .andExpect(jsonPath("$.pending[*].userId").value(contains(carol.toString())));
    }

    @Test
    void tumUygunOkuyucularAyrildiysaReadVeListelerBos() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID carol = newUser();
        UUID group = createGroupAndGetId(alice, bob);
        long seq = send(alice, group, "Eski mesaj");
        addMembers(alice, group, carol).andExpect(status().isOk());
        leave(bob, group).andExpect(status().isNoContent());

        // Spec §3.2 kabul edilmiş uç durum: durum geçmişle aynı formülden gelir, listeler boştur.
        history(alice, group, "").andExpect(jsonPath("$.items[0].readStatus").value("READ"));
        messageReads(alice, group, messageId(group, seq)).andExpect(status().isOk())
                .andExpect(jsonPath("$.readStatus").value("READ"))
                .andExpect(jsonPath("$.readers", empty()))
                .andExpect(jsonPath("$.pending", empty()));
    }

    @Test
    void gonderenDegilse403() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID group = createGroupAndGetId(alice, bob);
        long seq = send(alice, group, "Benim");

        messageReads(bob, group, messageId(group, seq))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_MESSAGE_SENDER"));
    }

    @Test
    void uyeDegilseVeCikarilmisGonderen404() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID owner = newUser();
        UUID outsider = newUser();
        UUID group = createGroupAndGetId(owner, alice, bob);
        long seq = send(alice, group, "Eski mesajım");
        UUID messageId = messageId(group, seq);

        messageReads(outsider, group, messageId).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONVERSATION_NOT_FOUND"));

        removeMember(owner, group, alice).andExpect(status().isNoContent());
        messageReads(alice, group, messageId).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONVERSATION_NOT_FOUND"));
    }

    @Test
    void baskaSohbetinMesajiVeGorunmeyenMesaj404() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID carol = newUser();
        UUID group = createGroupAndGetId(alice, bob);
        UUID other = openDirectAndGetId(alice, carol);
        long seq = send(alice, group, "Gruptaki");
        long otherSeq = send(alice, other, "Başka sohbette");
        addMembers(alice, group, carol).andExpect(status().isOk());

        // Başka sohbetin mesajı.
        messageReads(alice, group, messageId(other, otherSeq)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MESSAGE_NOT_FOUND"));
        // Var olmayan mesaj.
        messageReads(alice, group, UUID.randomUUID()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MESSAGE_NOT_FOUND"));
        // Katılmadan önceki mesaj: carol gönderen olamaz, 403 yerine 404 alır.
        messageReads(carol, group, messageId(group, seq)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MESSAGE_NOT_FOUND"));
    }

    @Test
    void migrationOncesiOkumaSaatsiz() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID carol = newUser();
        UUID group = createGroupAndGetId(alice, bob, carol);
        long seq = send(alice, group, "Herkese");
        markRead(bob, group, seq).andExpect(status().isOk());
        markRead(carol, group, seq).andExpect(status().isOk());
        jdbc.update("delete from message_read_ranges where conversation_id = ? and user_id = ?", group, bob);

        messageReads(alice, group, messageId(group, seq)).andExpect(status().isOk())
                .andExpect(jsonPath("$.readStatus").value("READ"))
                .andExpect(jsonPath("$.readers[*].userId").value(contains(carol.toString(), bob.toString())))
                .andExpect(jsonPath("$.readers[1].readAt").value(nullValue()))
                .andExpect(jsonPath("$.pending", empty()));
    }

    @Test
    void birebirdeTekKisi() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);
        long seq = send(alice, direct, "Selam");
        UUID messageId = messageId(direct, seq);

        messageReads(alice, direct, messageId).andExpect(status().isOk())
                .andExpect(jsonPath("$.readStatus").value("SENT"))
                .andExpect(jsonPath("$.readers", empty()))
                .andExpect(jsonPath("$.pending[0].userId").value(bob.toString()))
                .andExpect(jsonPath("$.pending", hasSize(1)));

        markRead(bob, direct, seq).andExpect(status().isOk());

        messageReads(alice, direct, messageId).andExpect(status().isOk())
                .andExpect(jsonPath("$.readStatus").value("READ"))
                .andExpect(jsonPath("$.readers", hasSize(1)))
                .andExpect(jsonPath("$.readers[0].userId").value(bob.toString()))
                .andExpect(jsonPath("$.pending", empty()));
    }

    private UUID messageId(UUID conversationId, long seq) {
        return jdbc.queryForObject("select id from messages where conversation_id = ? and seq = ?", UUID.class,
                conversationId, seq);
    }

    private void setReadAt(UUID conversationId, UUID userId, Instant readAt) {
        jdbc.update("update message_read_ranges set read_at = ? where conversation_id = ? and user_id = ?",
                Timestamp.from(readAt), conversationId, userId);
    }
}

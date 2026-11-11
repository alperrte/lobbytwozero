package com.ltz.messaging.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ltz.messaging.IntegrationTestSupport;
import com.ltz.messaging.application.service.MessageService;

/**
 * MSG-11 okuma saati: okuma işaretçisi her gerçekten ilerlediğinde tek bir aralık satırı yazılır (spec §3.3);
 * ilerlemeyen okuma, katılma ve yeniden katılma satır yazmaz. Aynı kullanıcının eşzamanlı okumaları çakışmayan ve
 * boşluksuz aralıklar üretir (spec §7).
 */
class ReadRangeTest extends IntegrationTestSupport {

    @Autowired
    private MessageService messages;

    @Test
    void okumaIlerleyinceTekAralikYazilir() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);
        for (int i = 1; i <= 3; i++) {
            send(alice, direct, "Mesaj " + i);
        }

        markRead(bob, direct, 3).andExpect(status().isOk())
                .andExpect(jsonPath("$.lastReadSeq").value(3))
                .andExpect(jsonPath("$.unreadCount").value(0));

        assertThat(ranges(direct, bob)).containsExactly(new long[] {1, 3});
    }

    @Test
    void ilerlemeyenOkumaSatirYazmaz() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);
        for (int i = 1; i <= 3; i++) {
            send(alice, direct, "Mesaj " + i);
        }
        markRead(bob, direct, 3).andExpect(status().isOk());

        for (long seq : new long[] {2, 3, 999}) {
            markRead(bob, direct, seq).andExpect(status().isOk())
                    .andExpect(jsonPath("$.lastReadSeq").value(3))
                    .andExpect(jsonPath("$.unreadCount").value(0));
        }

        assertThat(ranges(direct, bob)).hasSize(1);
    }

    @Test
    void buyukBirikimTekSatir() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);
        // Dakikalık gönderim sınırına takılmamak için mesajlar doğrudan yazılır; sohbetin sırası da ilerletilir.
        jdbc.update("insert into messages (id, conversation_id, seq, sender_id, client_message_id, type, body, "
                + "created_at) select gen_random_uuid(), ?, s, ?, 'bulk-' || s, 'TEXT', 'Mesaj ' || s, now() "
                + "from generate_series(1, 150) s", direct, alice);
        jdbc.update("update conversations set last_message_seq = 150 where id = ?", direct);

        markRead(bob, direct, 150).andExpect(status().isOk())
                .andExpect(jsonPath("$.lastReadSeq").value(150))
                .andExpect(jsonPath("$.unreadCount").value(0));

        assertThat(ranges(direct, bob)).containsExactly(new long[] {1, 150});
    }

    @Test
    void gonderimGonderenIcinAralikYazar() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);
        send(alice, direct, "Mesaj 1");
        send(alice, direct, "Mesaj 2");

        send(bob, direct, "Yanıt");

        // Yanıtlayan, yanıtladığı mesajları okumuş sayılır.
        assertThat(ranges(direct, bob)).containsExactly(new long[] {1, 3});
        assertThat(ranges(direct, alice)).containsExactly(new long[] {1, 1}, new long[] {2, 2});
    }

    @Test
    void yenidenEklenmeAralikYazmaz() throws Exception {
        UUID owner = newUser();
        UUID bob = newUser();
        UUID group = createGroupAndGetId(owner, bob, newUser());
        removeMember(owner, group, bob).andExpect(status().isNoContent());
        send(owner, group, "Mesaj 1");
        send(owner, group, "Mesaj 2");

        addMembers(owner, group, bob).andExpect(status().isOk());

        assertThat(ranges(group, bob)).isEmpty();
    }

    @Test
    void eszamanliOkumalarCakismazAralikUretir() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);
        int total = 20;
        for (int i = 1; i <= total; i++) {
            send(alice, direct, "Mesaj " + i);
        }
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Long>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            for (long seq = 1; seq <= total; seq++) {
                long target = seq;
                results.add(pool.submit(() -> {
                    start.await();
                    return messages.markRead(bob, direct, target).lastReadSeq();
                }));
            }
            start.countDown();
            for (Future<Long> result : results) {
                assertThat(result.get()).isBetween(1L, (long) total);
            }
        }

        List<long[]> ranges = ranges(direct, bob);
        assertThat(ranges).isNotEmpty();
        long expectedFrom = 1;
        for (long[] range : ranges) {
            assertThat(range[0]).as("aralık başı").isEqualTo(expectedFrom);
            assertThat(range[1]).as("aralık sonu").isGreaterThanOrEqualTo(range[0]);
            expectedFrom = range[1] + 1;
        }
        assertThat(expectedFrom - 1).as("birleşimin sonu").isEqualTo(total);
        assertThat(jdbc.queryForObject("select last_read_seq from conversation_members "
                + "where conversation_id = ? and user_id = ?", Long.class, direct, bob)).isEqualTo(total);
    }

    /** Kullanıcının sohbetteki okuma aralıkları {@code [fromSeq, toSeq]}, başlangıca göre sıralı. */
    private List<long[]> ranges(UUID conversationId, UUID userId) {
        return jdbc.query("select from_seq, to_seq from message_read_ranges "
                        + "where conversation_id = ? and user_id = ? order by from_seq",
                (row, index) -> new long[] {row.getLong("from_seq"), row.getLong("to_seq")}, conversationId, userId);
    }
}

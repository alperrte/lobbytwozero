package com.ltz.messaging.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.ltz.messaging.IntegrationTestSupport;
import com.ltz.messaging.application.service.ReadAdvance;
import com.ltz.messaging.application.service.ReadTracker;
import com.ltz.messaging.domain.entity.ConversationMember;
import com.ltz.messaging.infrastructure.persistence.repository.ConversationMemberRepository;

/**
 * MSG-11 canlı okundu bilgisi (spec §5): işaretçi ilerleyip commit edilince {@code read} olayı yalnız aralıkta mesajı
 * olan aktif göndericilere gider; yük alıcının kendi mesajlarıyla kırpılır (ham işaretçi ve ham su seviyesi sızmaz),
 * okuyucunun göremediği mesajlar için olay üretilmez. Geri alınan ve ilerlemeyen okuma olay üretmez.
 */
class ReadReceiptStreamTest extends IntegrationTestSupport {

    private static final Duration WAIT = Duration.ofSeconds(5);

    @Autowired
    private ThreadPoolTaskExecutor streamDispatchExecutor;

    @Autowired
    private ReadTracker tracker;

    @Autowired
    private ConversationMemberRepository members;

    @Autowired
    private TransactionTemplate transactions;

    @Test
    void okumaYalnizAraliktaMesajiOlanGondereneGider() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID carol = newUser();
        UUID reader = newUser();
        UUID group = createGroupAndGetId(alice, bob, carol, reader);
        MvcResult aliceStream = openStream(alice);
        MvcResult bobStream = openStream(bob);
        MvcResult carolStream = openStream(carol);
        insertMessages(group, alice, bob);

        markRead(reader, group, 1).andExpect(status().isOk());

        await().atMost(WAIT).untilAsserted(() -> assertThat(readEvents(aliceStream)).hasSize(1));
        drainDispatch();
        DocumentContext event = readEvents(aliceStream).getFirst();
        assertThat(event.read("$.readerId", String.class)).isEqualTo(reader.toString());
        assertThat(number(event, "$.readUpToSeq")).isEqualTo(1);
        // B'nin mesajı aralıkta değil, C'nin hiç mesajı yok.
        assertThat(readEvents(bobStream)).isEmpty();
        assertThat(readEvents(carolStream)).isEmpty();
    }

    @Test
    void yukAlicininKendiMesajlarinaKirpilir() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID filler = newUser();
        UUID reader = newUser();
        UUID group = createGroupAndGetId(filler, alice, bob, reader);
        MvcResult aliceStream = openStream(alice);
        MvcResult bobStream = openStream(bob);
        // A seq 5, B seq 9; aradakileri üçüncü bir üye yazar. Kimse henüz okumadı.
        insertMessages(group, filler, filler, filler, filler, alice, filler, filler, filler, bob);

        markRead(reader, group, 9).andExpect(status().isOk());

        await().atMost(WAIT).untilAsserted(() -> assertThat(readEvents(aliceStream)).hasSize(1));
        await().atMost(WAIT).untilAsserted(() -> assertThat(readEvents(bobStream)).hasSize(1));
        DocumentContext toAlice = readEvents(aliceStream).getFirst();
        Map<String, Object> payload = toAlice.json();
        assertThat(payload).containsOnlyKeys("conversationId", "readerId", "readUpToSeq", "readAt", "allReadUpToSeq");
        assertThat(toAlice.read("$.conversationId", String.class)).isEqualTo(group.toString());
        assertThat(toAlice.read("$.readerId", String.class)).isEqualTo(reader.toString());
        assertThat(toAlice.read("$.readAt", String.class)).isNotBlank();
        // Ham işaretçi (9) A'ya sızmaz: A, R'nin B'nin mesajını okuduğunu öğrenemez.
        assertThat(number(toAlice, "$.readUpToSeq")).isEqualTo(5);
        assertThat(number(toAlice, "$.allReadUpToSeq")).isZero();
        DocumentContext toBob = readEvents(bobStream).getFirst();
        assertThat(number(toBob, "$.readUpToSeq")).isEqualTo(9);
        assertThat(number(toBob, "$.allReadUpToSeq")).isZero();
    }

    @Test
    void birebirdeIcIceMesajlar() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);
        MvcResult aliceStream = openStream(alice);
        insertMessages(direct, alice, bob, alice, bob);

        markRead(bob, direct, 4).andExpect(status().isOk());

        await().atMost(WAIT).untilAsserted(() -> assertThat(readEvents(aliceStream)).hasSize(1));
        DocumentContext event = readEvents(aliceStream).getFirst();
        assertThat(number(event, "$.readUpToSeq")).isEqualTo(3);
        assertThat(number(event, "$.allReadUpToSeq")).isEqualTo(3);
    }

    @Test
    void buyukBirikimAliciBasinaTekOlay() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);
        MvcResult aliceStream = openStream(alice);
        // Dakikalık gönderim sınırına takılmamak için mesajlar doğrudan yazılır.
        insertMessages(direct, Collections.nCopies(150, alice).toArray(UUID[]::new));

        markRead(bob, direct, 150).andExpect(status().isOk());

        await().atMost(WAIT).untilAsserted(() -> assertThat(readEvents(aliceStream)).isNotEmpty());
        drainDispatch();
        List<DocumentContext> events = readEvents(aliceStream);
        assertThat(events).hasSize(1);
        assertThat(number(events.getFirst(), "$.readUpToSeq")).isEqualTo(150);
    }

    @Test
    void gonderimdeIlerlemeOlayUretir() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);
        MvcResult aliceStream = openStream(alice);
        send(alice, direct, "Soru");

        // Yanıtlayan, önündeki mesajları okumuş sayılır.
        send(bob, direct, "Yanıt");

        await().atMost(WAIT).untilAsserted(() -> assertThat(readEvents(aliceStream)).hasSize(1));
        DocumentContext event = readEvents(aliceStream).getFirst();
        assertThat(event.read("$.readerId", String.class)).isEqualTo(bob.toString());
        assertThat(number(event, "$.readUpToSeq")).isEqualTo(1);
        assertThat(number(event, "$.allReadUpToSeq")).isEqualTo(1);
    }

    @Test
    void ilerlemeyenOkumaOlayUretmez() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);
        MvcResult aliceStream = openStream(alice);
        insertMessages(direct, alice);
        markRead(bob, direct, 1).andExpect(status().isOk());

        markRead(bob, direct, 1).andExpect(status().isOk());

        drainDispatch();
        assertThat(readEvents(aliceStream)).hasSize(1);
    }

    @Test
    void geriAlinanIslemdeOlayGitmez() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);
        MvcResult aliceStream = openStream(alice);
        insertMessages(direct, alice);

        Optional<ReadAdvance> advance = transactions.execute(status -> {
            ConversationMember member = members.findActive(direct, bob).orElseThrow();
            Optional<ReadAdvance> advanced = tracker.advance(member, 1, Instant.now());
            status.setRollbackOnly();
            return advanced;
        });

        assertThat(advance).isPresent();
        drainDispatch();
        assertThat(readEvents(aliceStream)).isEmpty();
    }

    @Test
    void yeniKatilanGoremedigiMesajlarIcinOlayUretmez() throws Exception {
        UUID owner = newUser();
        UUID alice = newUser();
        UUID newcomer = newUser();
        UUID group = createGroupAndGetId(owner, alice);
        MvcResult ownerStream = openStream(owner);
        MvcResult aliceStream = openStream(alice);
        insertMessages(group, alice, alice);
        addMembers(owner, group, newcomer).andExpect(status().isOk());
        insertMessages(group, owner);

        // İlk okuma aralığı 1'den başlar, ama yeni üye yalnız 3'ü görebilir.
        markRead(newcomer, group, 3).andExpect(status().isOk());

        await().atMost(WAIT).untilAsserted(() -> assertThat(readEvents(ownerStream)).hasSize(1));
        drainDispatch();
        assertThat(number(readEvents(ownerStream).getFirst(), "$.readUpToSeq")).isEqualTo(3);
        assertThat(readEvents(aliceStream)).isEmpty();
    }

    @Test
    void ayrilanGondereneOlayGitmez() throws Exception {
        UUID owner = newUser();
        UUID alice = newUser();
        UUID reader = newUser();
        UUID group = createGroupAndGetId(owner, alice, reader);
        MvcResult ownerStream = openStream(owner);
        MvcResult aliceStream = openStream(alice);
        insertMessages(group, alice);
        leave(alice, group).andExpect(status().isNoContent());

        // Aralıktaki tek gönderici ayrılmış: kimseye olay gitmez.
        markRead(reader, group, 1).andExpect(status().isOk());

        drainDispatch();
        assertThat(readEvents(aliceStream)).isEmpty();
        assertThat(readEvents(ownerStream)).isEmpty();
    }

    @Test
    void ayrilanUyeDurumuReadeCevirirseGondereneGider() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID carol = newUser();
        UUID group = createGroupAndGetId(alice, bob, carol);
        insertMessages(group, alice);
        markRead(bob, group, 1).andExpect(status().isOk());
        MvcResult aliceStream = openStream(alice);
        MvcResult carolStream = openStream(carol);
        drainDispatch();
        // B okudu ama C okumadı: mesaj henüz SENT.
        assertThat(readEvents(aliceStream)).isEmpty();

        leave(carol, group).andExpect(status().isNoContent());

        await().atMost(WAIT).untilAsserted(() -> assertThat(readEvents(aliceStream)).hasSize(1));
        drainDispatch();
        assertDeparture(readEvents(aliceStream).getFirst(), group, 1);
        // Ayrılan kişi bu olayı almaz.
        assertThat(readEvents(carolStream)).isEmpty();
    }

    @Test
    void cikarilanUyeIcinDeAyni() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID carol = newUser();
        UUID group = createGroupAndGetId(alice, bob, carol);
        insertMessages(group, alice);
        markRead(bob, group, 1).andExpect(status().isOk());
        MvcResult aliceStream = openStream(alice);
        MvcResult bobStream = openStream(bob);
        MvcResult carolStream = openStream(carol);

        removeMember(alice, group, carol).andExpect(status().isNoContent());

        await().atMost(WAIT).untilAsserted(() -> assertThat(readEvents(aliceStream)).hasSize(1));
        drainDispatch();
        assertDeparture(readEvents(aliceStream).getFirst(), group, 1);
        // B'nin mesajı yok; çıkarılan C de olay almaz.
        assertThat(readEvents(bobStream)).isEmpty();
        assertThat(readEvents(carolStream)).isEmpty();
    }

    @Test
    void durumDegismeyeneGitmez() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID carol = newUser();
        UUID group = createGroupAndGetId(alice, bob, carol);
        // A seq 1, B seq 2. B 1'i, C ise 2'ye kadar hepsini okudu; A hiçbir şey okumadı.
        insertMessages(group, alice, bob);
        markRead(bob, group, 1).andExpect(status().isOk());
        markRead(carol, group, 2).andExpect(status().isOk());
        MvcResult aliceStream = openStream(alice);
        MvcResult bobStream = openStream(bob);
        drainDispatch();

        leave(carol, group).andExpect(status().isNoContent());

        // A için su seviyesi B'nin işaretçisinde (1) kaldı; B için A'nın işaretçisinde (0) kaldı: kimseye olay yok.
        drainDispatch();
        assertThat(readEvents(aliceStream)).isEmpty();
        assertThat(readEvents(bobStream)).isEmpty();
    }

    @Test
    void suSeviyesiYukselipGondericiningYeniMesajiYoksaGitmez() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID carol = newUser();
        UUID group = createGroupAndGetId(alice, bob, carol);
        // A seq 1, B seq 2. B ikisini, C ise yalnız 1'i okudu. A için su seviyesi 1'den 2'ye çıkacak, ama A'nın
        // 1'den büyük bir mesajı yok: durumu değişen mesaj da yok.
        insertMessages(group, alice, bob);
        markRead(bob, group, 2).andExpect(status().isOk());
        markRead(carol, group, 1).andExpect(status().isOk());
        MvcResult aliceStream = openStream(alice);
        drainDispatch();

        leave(carol, group).andExpect(status().isNoContent());

        drainDispatch();
        assertThat(readEvents(aliceStream)).isEmpty();
    }

    @Test
    void ayrilanSonrasiSuSeviyesiYukselenHerGondereneGider() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID carol = newUser();
        UUID group = createGroupAndGetId(alice, bob, carol);
        // A seq 1, B seq 2. A ile B birbirinin mesajını okudu; yalnız C geride (işaretçisi 0).
        insertMessages(group, alice, bob);
        markRead(alice, group, 2).andExpect(status().isOk());
        markRead(bob, group, 2).andExpect(status().isOk());
        MvcResult aliceStream = openStream(alice);
        MvcResult bobStream = openStream(bob);
        drainDispatch();

        leave(carol, group).andExpect(status().isNoContent());

        // C ayrılınca engel kalktı: iki mesaj da kalan tüm üyelerce okunmuş sayılır.
        await().atMost(WAIT).untilAsserted(() -> assertThat(readEvents(aliceStream)).hasSize(1));
        await().atMost(WAIT).untilAsserted(() -> assertThat(readEvents(bobStream)).hasSize(1));
        drainDispatch();
        assertDeparture(readEvents(aliceStream).getFirst(), group, 1);
        assertDeparture(readEvents(bobStream).getFirst(), group, 2);
    }

    @Test
    void yenidenEklenenGondericininGoremedigiMesajIcinOlayGitmez() throws Exception {
        UUID owner = newUser();
        UUID alice = newUser();
        UUID reader = newUser();
        UUID group = createGroupAndGetId(owner, alice, reader);
        // A seq 3'ü yazdı, ayrıldı; 4-7 sonra yazıldı ve A seq 8'den itibaren görecek şekilde yeniden eklendi.
        insertMessages(group, owner, owner, alice);
        leave(alice, group).andExpect(status().isNoContent());
        insertMessages(group, owner, owner, owner, owner);
        addMembers(owner, group, alice).andExpect(status().isOk());
        MvcResult ownerStream = openStream(owner);
        MvcResult aliceStream = openStream(alice);

        markRead(reader, group, 7).andExpect(status().isOk());

        // Sahibin mesajları görünür: olay ona gider. A'nın tek mesajı kendi sınırının altında: A olay almaz.
        await().atMost(WAIT).untilAsserted(() -> assertThat(readEvents(ownerStream)).hasSize(1));
        drainDispatch();
        assertThat(number(readEvents(ownerStream).getFirst(), "$.readUpToSeq")).isEqualTo(7);
        assertThat(readEvents(aliceStream)).isEmpty();
    }

    @Test
    void ayrilanUyeYenidenEklenenGondericininGoremedigiMesajiCevirirseOlayGitmez() throws Exception {
        UUID owner = newUser();
        UUID alice = newUser();
        UUID bob = newUser();
        UUID group = createGroupAndGetId(owner, alice, bob);
        insertMessages(group, owner, owner, alice);
        leave(alice, group).andExpect(status().isNoContent());
        insertMessages(group, owner, owner, owner, owner);
        addMembers(owner, group, alice).andExpect(status().isOk());
        markRead(owner, group, 7).andExpect(status().isOk());
        MvcResult ownerStream = openStream(owner);
        MvcResult aliceStream = openStream(alice);
        drainDispatch();

        // B ayrılınca A için su seviyesi 0'dan 7'ye çıkar; ama A'nın tek mesajı (3) kendi sınırının altında.
        leave(bob, group).andExpect(status().isNoContent());

        await().atMost(WAIT).untilAsserted(() -> assertThat(readEvents(ownerStream)).hasSize(1));
        drainDispatch();
        assertDeparture(readEvents(ownerStream).getFirst(), group, 7);
        assertThat(readEvents(aliceStream)).isEmpty();
    }

    /** Ayrılma/çıkarma olayı: okuyucu alanları null, yalnız {@code allReadUpToSeq} dolu. */
    private static void assertDeparture(DocumentContext event, UUID group, long allReadUpToSeq) {
        Map<String, Object> payload = event.json();
        assertThat(payload).containsOnlyKeys("conversationId", "readerId", "readUpToSeq", "readAt", "allReadUpToSeq");
        assertThat(event.read("$.conversationId", String.class)).isEqualTo(group.toString());
        assertThat(payload.get("readerId")).isNull();
        assertThat(payload.get("readUpToSeq")).isNull();
        assertThat(payload.get("readAt")).isNull();
        assertThat(number(event, "$.allReadUpToSeq")).isEqualTo(allReadUpToSeq);
    }

    /**
     * Mesajları gönderim yolunu atlayarak (oran sınırı ve gönderenin işaretçi ilerlemesi olmadan) sırayla yazar; sohbetin
     * sırası da ilerletilir. {@code senders[i]}, sohbetin sıradaki seq'inin göndericisidir.
     */
    private void insertMessages(UUID conversationId, UUID... senders) {
        long last = jdbc.queryForObject("select last_message_seq from conversations where id = ?", Long.class,
                conversationId);
        for (UUID sender : senders) {
            last++;
            jdbc.update("insert into messages (id, conversation_id, seq, sender_id, client_message_id, type, body, "
                    + "created_at) values (gen_random_uuid(), ?, ?, ?, ?, 'TEXT', ?, now())",
                    conversationId, last, sender, UUID.randomUUID().toString(), "Mesaj " + last);
        }
        jdbc.update("update conversations set last_message_seq = ? where id = ?", last, conversationId);
    }

    /**
     * Akış yürütücüsünün önceden kuyruğa giren tüm işleri bitirmesini bekler: her iş parçacığı bir engel işi alır;
     * hepsi aynı anda engeldeyse daha önceki işlerin hepsi (kuyruk sırasıyla) tamamlanmıştır. Olay gitmediğini
     * kanıtlamak için gerekir.
     */
    private void drainDispatch() throws Exception {
        int threads = streamDispatchExecutor.getMaxPoolSize();
        CyclicBarrier barrier = new CyclicBarrier(threads + 1);
        for (int i = 0; i < threads; i++) {
            streamDispatchExecutor.execute(() -> {
                try {
                    barrier.await(WAIT.toSeconds(), TimeUnit.SECONDS);
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });
        }
        barrier.await(WAIT.toSeconds(), TimeUnit.SECONDS);
    }

    private MvcResult openStream(UUID user) throws Exception {
        MvcResult result = mockMvc.perform(get(MessagingApiPaths.STREAM)
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(request().asyncStarted())
                .andReturn();
        await().atMost(WAIT).untilAsserted(() -> assertThat(content(result)).contains("event:ready"));
        return result;
    }

    /** Akıştaki {@code read} olaylarının yükleri, geliş sırasıyla. Okundu olayı {@code id} taşımaz. */
    private static List<DocumentContext> readEvents(MvcResult stream) throws Exception {
        List<String> blocks = Arrays.stream(content(stream).split("\n\n"))
                .filter(block -> block.lines().anyMatch("event:read"::equals))
                .toList();
        blocks.forEach(block -> assertThat(block.lines()).noneMatch(line -> line.startsWith("id:")));
        return blocks.stream()
                .map(block -> block.lines().filter(line -> line.startsWith("data:")).findFirst().orElseThrow())
                .map(data -> JsonPath.parse(data.substring("data:".length())))
                .toList();
    }

    private static long number(DocumentContext event, String path) {
        return event.read(path, Number.class).longValue();
    }

    /** SSE her zaman UTF-8'dir (tarayıcı da öyle çözer). */
    private static String content(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}

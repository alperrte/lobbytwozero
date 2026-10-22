package com.ltz.social.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ltz.social.IntegrationTestSupport;
import com.ltz.social.domain.exception.SocialException;

/**
 * Engelleme ile aynı anda yapılan istek/kabul işlemleri tutarlı kalır: engel konduktan sonra aralarında bekleyen istek
 * ya da arkadaşlık kalamaz (SOC-09). Her tur farklı bir sıralama denenir.
 */
class BlockConcurrencyTest extends IntegrationTestSupport {

    private static final int ROUNDS = 25;

    @Autowired
    private FriendRequestService friendRequests;

    @Autowired
    private BlockService blockService;

    @Test
    void engelleIstekGondermeYarisi_engelSonrasiBekleyenIstekKalmaz() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            UUID sender = newUser();
            UUID blocker = newUser();

            run(() -> attempt(() -> friendRequests.send(sender, blocker)),
                    () -> attempt(() -> blockService.block(blocker, sender)));

            assertThat(isBlocked(blocker, sender)).isTrue();
            assertThat(pendingBetween(sender, blocker)).isZero();
            assertThat(areFriends(sender, blocker)).isFalse();
        }
    }

    @Test
    void engelleKabulYarisi_engelSonrasiArkadaslikKalmaz() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            UUID sender = newUser();
            UUID receiver = newUser();
            UUID requestId = friendRequests.send(sender, receiver).id();

            run(() -> attempt(() -> friendRequests.accept(receiver, requestId)),
                    () -> attempt(() -> blockService.block(receiver, sender)));

            assertThat(isBlocked(receiver, sender)).isTrue();
            assertThat(pendingBetween(sender, receiver)).isZero();
            assertThat(areFriends(sender, receiver)).isFalse();
            assertThat(requestStatus(requestId)).isIn("ACCEPTED", "REJECTED");
        }
    }

    @Test
    void ayniEngelinEszamanliKonulmasi_tekKayitTekOlay() throws Exception {
        UUID blocker = newUser();
        UUID blocked = newUser();

        List<String> outcomes = runAll(8, () -> attempt(() -> blockService.block(blocker, blocked)));

        assertThat(outcomes).containsOnly("OK");
        assertThat(jdbc.queryForObject("select count(*) from user_blocks where blocker_id = ? and blocked_id = ?",
                Integer.class, blocker, blocked)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where event_type = 'UserBlocked' and "
                + "payload ->> 'blockerId' = ? and payload ->> 'blockedId' = ?", Integer.class, blocker.toString(),
                blocked.toString())).isEqualTo(1);
    }

    @Test
    void karsiYonluEszamanliEngeller_ikisiDeKonur() throws Exception {
        UUID first = newUser();
        UUID second = newUser();

        run(() -> attempt(() -> blockService.block(first, second)),
                () -> attempt(() -> blockService.block(second, first)));

        assertThat(isBlocked(first, second)).isTrue();
        assertThat(isBlocked(second, first)).isTrue();
    }

    private static String attempt(Runnable action) {
        try {
            action.run();
            return "OK";
        } catch (SocialException exception) {
            return exception.errorCode().name();
        }
    }

    @SafeVarargs
    private static void run(Callable<String>... tasks) throws InterruptedException, ExecutionException {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.length);
        try {
            CountDownLatch ready = new CountDownLatch(tasks.length);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<String>> futures = new ArrayList<>();
            for (Callable<String> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return task.call();
                }));
            }
            ready.await();
            go.countDown();
            for (Future<String> future : futures) {
                future.get();
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static List<String> runAll(int copies, Callable<String> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(copies);
        try {
            CountDownLatch go = new CountDownLatch(1);
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < copies; i++) {
                futures.add(pool.submit(() -> {
                    go.await();
                    return task.call();
                }));
            }
            go.countDown();
            List<String> results = new ArrayList<>();
            for (Future<String> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}

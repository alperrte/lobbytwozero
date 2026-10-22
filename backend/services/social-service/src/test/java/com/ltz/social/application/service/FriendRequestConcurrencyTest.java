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
import java.util.function.IntFunction;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ltz.social.IntegrationTestSupport;
import com.ltz.social.domain.exception.SocialErrorCode;
import com.ltz.social.domain.exception.SocialException;

/** Eşzamanlı istekler DB kısıtı ve satır kilidiyle tutarlı kalır (SECURITY.md §9, §12). */
class FriendRequestConcurrencyTest extends IntegrationTestSupport {

    private static final String OK = "OK";
    private static final int THREADS = 8;
    private static final int RACE_ROUNDS = 15;

    @Autowired
    private FriendRequestService service;

    @Test
    void ayniCiftIcinEszamanliIstekler_tekBekleyenKayitOlusur() throws Exception {
        UUID sender = newUser();
        UUID receiver = newUser();

        List<String> outcomes = race(THREADS, index -> () -> outcome(() -> service.send(sender, receiver)));

        assertThat(outcomes).filteredOn(OK::equals).hasSize(1);
        assertThat(outcomes).filteredOn(result -> !OK.equals(result))
                .containsOnly(SocialErrorCode.FRIEND_REQUEST_ALREADY_PENDING.name());
        assertThat(pendingBetween(sender, receiver)).isEqualTo(1);
    }

    @Test
    void karsiYonluEszamanliIstekler_tekBekleyenKayitOlusur() throws Exception {
        UUID first = newUser();
        UUID second = newUser();

        List<String> outcomes = race(THREADS, index -> () -> outcome(index % 2 == 0
                ? () -> service.send(first, second)
                : () -> service.send(second, first)));

        assertThat(outcomes).filteredOn(OK::equals).hasSize(1);
        assertThat(outcomes).filteredOn(result -> !OK.equals(result)).isNotEmpty().allSatisfy(result ->
                assertThat(result).isIn(SocialErrorCode.FRIEND_REQUEST_ALREADY_PENDING.name(),
                        SocialErrorCode.INCOMING_FRIEND_REQUEST_PENDING.name()));
        assertThat(pendingBetween(first, second)).isEqualTo(1);
    }

    @Test
    void ayniIstegiEszamanliKabul_tekArkadaslikKaydiVeTekBasari() throws Exception {
        UUID sender = newUser();
        UUID receiver = newUser();
        UUID requestId = service.send(sender, receiver).id();

        List<String> outcomes = race(THREADS, index -> () -> outcome(() -> service.accept(receiver, requestId)));

        assertThat(outcomes).filteredOn(OK::equals).hasSize(1);
        assertThat(outcomes).filteredOn(result -> !OK.equals(result))
                .containsOnly(SocialErrorCode.FRIEND_REQUEST_NOT_PENDING.name());
        assertThat(areFriends(sender, receiver)).isTrue();
        assertThat(friendshipRows(sender, receiver)).isEqualTo(1);
    }

    @Test
    void kabulIleIptalYarisi_tutarliSonucVerir() throws Exception {
        for (int round = 0; round < RACE_ROUNDS; round++) {
            UUID sender = newUser();
            UUID receiver = newUser();
            UUID requestId = service.send(sender, receiver).id();

            List<String> outcomes = race(2, index -> () -> outcome(index == 0
                    ? () -> service.accept(receiver, requestId)
                    : () -> service.cancel(sender, requestId)));

            // Tam olarak biri kazanır, diğeri güncel durumu görüp 409 alır.
            assertThat(outcomes).filteredOn(OK::equals).hasSize(1);
            assertThat(outcomes).filteredOn(result -> !OK.equals(result))
                    .containsOnly(SocialErrorCode.FRIEND_REQUEST_NOT_PENDING.name());
            // Durum ve arkadaşlık birbiriyle çelişemez.
            String status = requestStatus(requestId);
            assertThat(status).isIn("ACCEPTED", "CANCELLED");
            assertThat(areFriends(sender, receiver)).isEqualTo("ACCEPTED".equals(status));
        }
    }

    private interface Action {
        void run();
    }

    private static String outcome(Action action) {
        try {
            action.run();
            return OK;
        } catch (SocialException exception) {
            return exception.errorCode().name();
        }
    }

    /** Görevleri aynı anda başlatır ve her görevin sonucunu toplar. */
    private static List<String> race(int tasks, IntFunction<Callable<String>> factory)
            throws InterruptedException, ExecutionException {
        ExecutorService pool = Executors.newFixedThreadPool(tasks);
        try {
            CountDownLatch ready = new CountDownLatch(tasks);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<String>> futures = new ArrayList<>();
            for (int index = 0; index < tasks; index++) {
                Callable<String> task = factory.apply(index);
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return task.call();
                }));
            }
            ready.await();
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

    private int friendshipRows(UUID first, UUID second) {
        return jdbc.queryForObject("select count(*) from friendships where "
                + "(user_low_id = ? and user_high_id = ?) or (user_low_id = ? and user_high_id = ?)",
                Integer.class, first, second, second, first);
    }
}

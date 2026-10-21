package com.ltz.auth;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;

/** Aynı anda başlatılan istekler; yarış koşulu testleri için ortak başlangıç kapısı kullanır. */
public final class ConcurrentRequests {

    private ConcurrentRequests() {
    }

    public static List<Integer> run(int count, IntFunction<Callable<Integer>> requestFactory) throws Exception {
        CountDownLatch startGate = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(count)) {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                Callable<Integer> request = requestFactory.apply(index);
                futures.add(executor.submit(() -> {
                    startGate.await();
                    return request.call();
                }));
            }
            startGate.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get());
            }
            return statuses;
        }
    }
}

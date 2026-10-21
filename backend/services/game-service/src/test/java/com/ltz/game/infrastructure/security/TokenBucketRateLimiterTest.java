package com.ltz.game.infrastructure.security;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Sahte saatli birim testleri; gerçek zaman beklenmez. */
class TokenBucketRateLimiterTest {
    static final long SECOND = 1_000_000_000L;
    final AtomicLong nanos = new AtomicLong(1_000 * SECOND);

    TokenBucketRateLimiter limiter(int capacity, double refill, int maxKeys) {
        return new TokenBucketRateLimiter(capacity, refill, maxKeys, nanos::get);
    }

    @Test void allowsTheBurstThenRejectsWithPositiveRetryAfter() {
        var limiter = limiter(5, 1, 100);
        for (int i = 0; i < 5; i++) assertTrue(limiter.tryAcquire("a").allowed());
        var rejected = limiter.tryAcquire("a");
        assertFalse(rejected.allowed());
        assertEquals(1, rejected.retryAfterSeconds());
        assertTrue(limiter.tryAcquire("b").allowed(), "başka anahtar etkilenmez");
    }

    @Test void refillsOverTimeButNeverBeyondCapacity() {
        var limiter = limiter(3, 1, 100);
        for (int i = 0; i < 3; i++) limiter.tryAcquire("a");
        assertFalse(limiter.tryAcquire("a").allowed());
        nanos.addAndGet(2 * SECOND);
        assertTrue(limiter.tryAcquire("a").allowed());
        assertTrue(limiter.tryAcquire("a").allowed());
        assertFalse(limiter.tryAcquire("a").allowed());
        nanos.addAndGet(1_000 * SECOND);
        for (int i = 0; i < 3; i++) assertTrue(limiter.tryAcquire("a").allowed());
        assertFalse(limiter.tryAcquire("a").allowed(), "uzun bekleme kapasiteyi aşan jeton biriktirmez");
    }

    @Test void retryAfterReflectsTheSlowRefillRate() {
        var limiter = limiter(1, 0.1, 100);
        assertTrue(limiter.tryAcquire("a").allowed());
        assertEquals(10, limiter.tryAcquire("a").retryAfterSeconds());
        nanos.addAndGet(4 * SECOND);
        assertEquals(6, limiter.tryAcquire("a").retryAfterSeconds());
    }

    @Test void trackedKeysAreBoundedAndOverflowKeysShareOneBucket() {
        var limiter = limiter(2, 0.001, 100);
        for (int i = 0; i < 100; i++) assertTrue(limiter.tryAcquire("key-" + i).allowed());
        assertEquals(100, limiter.trackedKeys());
        // Tablo dolu ve hiçbir kova boşta değil: yeni anahtarlar ortak taşma kovasını paylaşır, bellek büyümez.
        int allowed = 0;
        for (int i = 0; i < 1_000; i++) if (limiter.tryAcquire("attacker-" + i).allowed()) allowed++;
        assertEquals(2, allowed);
        assertEquals(100, limiter.trackedKeys());
        // Mevcut anahtarların hakkı taşmadan etkilenmez.
        assertTrue(limiter.tryAcquire("key-5").allowed());
    }

    @Test void idleFullBucketsAreEvictedSoLegitimateNewKeysFitAgain() {
        var limiter = limiter(2, 1, 100);
        for (int i = 0; i < 100; i++) limiter.tryAcquire("old-" + i);
        nanos.addAndGet(60 * SECOND);
        assertTrue(limiter.tryAcquire("fresh").allowed());
        assertTrue(limiter.trackedKeys() <= 100);
        assertTrue(limiter.tryAcquire("fresh").allowed());
        assertFalse(limiter.tryAcquire("fresh").allowed(), "taze anahtar kendi kovasında sınırlıdır, taşmaya düşmedi");
    }

    @Test void concurrentCallersNeverExceedTheCapacity() throws Exception {
        var limiter = limiter(100, 0.000001, 100);
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int t = 0; t < threads; t++) results.add(pool.submit(() -> {
            start.await();
            int allowed = 0;
            for (int i = 0; i < 200; i++) if (limiter.tryAcquire("shared").allowed()) allowed++;
            return allowed;
        }));
        start.countDown();
        int total = 0;
        for (Future<Integer> result : results) total += result.get(30, TimeUnit.SECONDS);
        pool.shutdownNow();
        assertEquals(100, total);
    }

    @Test void invalidConfigurationFailsFast() {
        assertThrows(IllegalStateException.class, () -> new RateLimitProperties.Batch(0, 1));
        assertThrows(IllegalStateException.class, () -> new RateLimitProperties.Batch(10, 0));
        assertThrows(IllegalStateException.class, () -> new RateLimitProperties.Batch(10, Double.NaN));
        var batch = new RateLimitProperties.Batch(10, 1);
        assertThrows(IllegalStateException.class, () -> new RateLimitProperties(batch, 10, null));
        assertTrue(new RateLimitProperties(batch, 1_000, null).trustedProxies().isEmpty());
    }

    @Test void concurrentDistinctKeysNeverExceedMaxKeysAndOverflowSharesItsBudget() throws Exception {
        var limiter = limiter(2, 0.000001, 4);
        int workers = 32;
        try (var pool = Executors.newFixedThreadPool(workers)) {
            var ready = new CountDownLatch(workers);
            var start = new CountDownLatch(1);
            List<Future<Integer>> results = new ArrayList<>();
            for (int index = 0; index < workers; index++) {
                String key = "concurrent-" + index;
                results.add(pool.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    int allowed = 0;
                    for (int attempt = 0; attempt < 3; attempt++) {
                        if (limiter.tryAcquire(key).allowed()) allowed++;
                        assertTrue(limiter.trackedKeys() <= 4);
                    }
                    return allowed;
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            int allowed = 0;
            for (var result : results) allowed += result.get(20, TimeUnit.SECONDS);
            assertEquals(4, limiter.trackedKeys());
            assertEquals(10, allowed, "Dört kayıtlı kova ve tek taşma kovası ikişer jeton sağlar");
        }
    }

    @Test void collidingNewKeysCannotRacePastTheLastAvailableSlot() throws Exception {
        int workers = 64;
        try (var pool = Executors.newFixedThreadPool(workers)) {
            for (int round = 0; round < 16; round++) {
                var limiter = limiter(1, 0.000001, 1);
                var ready = new CountDownLatch(workers);
                var start = new CountDownLatch(1);
                List<Future<Boolean>> results = new ArrayList<>();
                for (int index = 0; index < workers; index++) {
                    String key = collidingKey(index);
                    results.add(pool.submit(() -> {
                        ready.countDown();
                        assertTrue(start.await(10, TimeUnit.SECONDS));
                        return limiter.tryAcquire(key).allowed();
                    }));
                }
                assertTrue(ready.await(10, TimeUnit.SECONDS));
                start.countDown();
                int allowed = 0;
                for (var result : results) if (result.get(20, TimeUnit.SECONDS)) allowed++;
                assertEquals(1, limiter.trackedKeys());
                assertEquals(2, allowed, "Tek kayıtlı kova ve ortak taşma kovası birer jeton sağlar");
            }
        }
    }

    /** Aa ve BB aynı String hash'ini üretir; farklı anahtarlar aynı hash diliminde yarışır. */
    private static String collidingKey(int index) {
        StringBuilder key = new StringBuilder();
        for (int bit = 0; bit < 6; bit++) key.append(((index >> bit) & 1) == 0 ? "Aa" : "BB");
        return key.toString();
    }
}

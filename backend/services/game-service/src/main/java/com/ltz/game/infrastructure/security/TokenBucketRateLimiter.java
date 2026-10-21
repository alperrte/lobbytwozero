package com.ltz.game.infrastructure.security;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Anahtar başına jeton kovası. Süreç içi ve tek örnek içindir (Game Service'in kendi ucunu korur); dağıtık sınır değildir.
 * Bellek sınırlıdır: anahtar sayısı {@code maxKeys}'e ulaşınca önce boşta (dolu) kovalar temizlenir, yine yer yoksa yeni
 * anahtarlar tek bir ortak taşma kovasını paylaşır. Böylece sahte anahtar üretimi hem belleği şişiremez hem de sınırı atlatamaz.
 */
final class TokenBucketRateLimiter {
    record Decision(boolean allowed, long retryAfterSeconds) { }

    private static final class Bucket {
        private double tokens;
        private long lastNanos;

        Bucket(double tokens, long nanos) { this.tokens = tokens; this.lastNanos = nanos; }

        synchronized Decision take(long nanos, int capacity, double refillPerSecond) {
            refill(nanos, capacity, refillPerSecond);
            if (tokens >= 1) {
                tokens -= 1;
                return new Decision(true, 0);
            }
            long seconds = (long) Math.ceil((1 - tokens) / refillPerSecond);
            return new Decision(false, Math.max(1, seconds));
        }

        synchronized boolean isFull(long nanos, int capacity, double refillPerSecond) {
            refill(nanos, capacity, refillPerSecond);
            return tokens >= capacity;
        }

        private void refill(long nanos, int capacity, double refillPerSecond) {
            long elapsed = nanos - lastNanos;
            if (elapsed > 0) {
                tokens = Math.min(capacity, tokens + elapsed / 1_000_000_000.0 * refillPerSecond);
                lastNanos = nanos;
            }
        }
    }

    private static final long SWEEP_INTERVAL_NANOS = 1_000_000_000L;
    private final int capacity;
    private final double refillPerSecond;
    private final int maxKeys;
    private final LongSupplier nanoClock;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final Bucket overflow;
    private final AtomicLong lastSweep;

    TokenBucketRateLimiter(int capacity, double refillPerSecond, int maxKeys, LongSupplier nanoClock) {
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
        this.maxKeys = maxKeys;
        this.nanoClock = nanoClock;
        long now = nanoClock.getAsLong();
        this.overflow = new Bucket(capacity, now);
        this.lastSweep = new AtomicLong(now);
    }

    Decision tryAcquire(String key) {
        long now = nanoClock.getAsLong();
        Bucket bucket = buckets.get(key);
        if (bucket == null) bucket = admit(key, now);
        return bucket.take(now, capacity, refillPerSecond);
    }

    int trackedKeys() { return buckets.size(); }

    private Bucket admit(String key, long now) {
        if (buckets.size() >= maxKeys) {
            sweep(now);
            if (buckets.size() >= maxKeys) return overflow;
        }
        return buckets.computeIfAbsent(key, ignored -> new Bucket(capacity, now));
    }

    /** Dolu kovada hiçbir bilgi kaybolmaz; yalnız bunlar silinir. En çok saniyede bir çalışır. */
    private void sweep(long now) {
        long previous = lastSweep.get();
        if (now - previous < SWEEP_INTERVAL_NANOS || !lastSweep.compareAndSet(previous, now)) return;
        buckets.entrySet().removeIf(entry -> entry.getValue().isFull(now, capacity, refillPerSecond));
    }
}

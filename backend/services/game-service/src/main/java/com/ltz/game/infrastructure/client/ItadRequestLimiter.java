package com.ltz.game.infrastructure.client;

import com.ltz.game.domain.exception.ProviderException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.LongConsumer;

/**
 * ITAD istek sınırı için istemci tarafı kayan pencere sınırlayıcı. ITAD, 5 dakikalık pencerede 1000 isteğe izin verir; sınırlayıcı payı ({@code limit})
 * bunun altında tutar (yeniden denemeler ve elle ölçümler için pay bırakır), böylece kendi servisimizden kaynaklı 429 oluşmaz. Pencere doluysa en
 * eski isteğin pencereden çıkmasına kadar bekler; bekleme {@code maxWait}'i aşacaksa {@code PRICE_LOCAL_THROTTLE} fırlatır (çağıran turu bırakır, oyunları başarısız saymaz).
 */
final class ItadRequestLimiter {
    static final int DEFAULT_LIMIT = 850;
    static final Duration DEFAULT_WINDOW = Duration.ofMinutes(5);
    static final Duration DEFAULT_MAX_WAIT = Duration.ofSeconds(20);
    /** Kendi sınırımız dolduğunda atılan kod: ITAD hatası değildir, çağıran bunu oyun başarısızlığı olarak KAYDETMEMELİ, yalnız turu bırakmalıdır. */
    static final String LOCAL_THROTTLE = "PRICE_LOCAL_THROTTLE";
    private final Clock clock;
    private final int limit;
    private final Duration window;
    private final Duration maxWait;
    private final LongConsumer sleeper;
    private final Deque<Instant> recent = new ArrayDeque<>();

    ItadRequestLimiter(Clock clock) { this(clock, DEFAULT_LIMIT, DEFAULT_WINDOW, DEFAULT_MAX_WAIT, ItadRequestLimiter::sleepMillis); }

    ItadRequestLimiter(Clock clock, int limit, Duration window, Duration maxWait, LongConsumer sleeper) {
        if (limit < 1 || window.isNegative() || window.isZero() || maxWait.isNegative()) throw new IllegalArgumentException("Geçersiz ITAD hız sınırı.");
        this.clock = clock;
        this.limit = limit;
        this.window = window;
        this.maxWait = maxWait;
        this.sleeper = sleeper;
    }

    /** Bir istek hakkı alır; pencere doluysa gerekirse bekler ya da {@code PRICE_RATE_LIMITED} fırlatır. */
    void acquire() {
        while (true) {
            Duration wait = tryAcquire();
            if (wait == null) return;
            if (wait.compareTo(maxWait) > 0) throw new ProviderException(LOCAL_THROTTLE, wait);
            sleeper.accept(wait.toMillis() + 1);
        }
    }

    /** @return hak alındıysa null; aksi halde bir sonraki hakka kadar beklenecek süre */
    private synchronized Duration tryAcquire() {
        Instant now = clock.instant();
        while (!recent.isEmpty() && !recent.peekFirst().plus(window).isAfter(now)) recent.removeFirst();
        if (recent.size() < limit) {
            recent.addLast(now);
            return null;
        }
        return Duration.between(now, recent.peekFirst().plus(window));
    }

    private static void sleepMillis(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            throw new ProviderException("PRICE_UNAVAILABLE");
        }
    }
}

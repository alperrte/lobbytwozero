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
 *
 * <p>Payın {@code reserve} kadarı sayfası açılan oyunlara (etkileşimli) ayrılmıştır: toplu arka plan turları pencerede en çok {@code limit - reserve} isteğe
 * ulaşabilir, etkileşimli istekler ise {@code limit}'e kadar. Böylece toplu tur payı tüketse bile kullanıcının baktığı oyunun fiyatı yenilenebilir.
 */
final class ItadRequestLimiter {
    static final int DEFAULT_LIMIT = 850;
    /** Pencere payından sayfası açılan oyunlara ayrılan kısım (≈%18): 5 dakikada yüzlerce sayfa açılışına yeter. */
    static final int DEFAULT_RESERVE = 150;
    static final Duration DEFAULT_WINDOW = Duration.ofMinutes(5);
    static final Duration DEFAULT_MAX_WAIT = Duration.ofSeconds(20);
    /** Kendi sınırımız dolduğunda atılan kod: ITAD hatası değildir, çağıran bunu oyun başarısızlığı olarak KAYDETMEMELİ, yalnız turu bırakmalıdır. */
    static final String LOCAL_THROTTLE = "PRICE_LOCAL_THROTTLE";
    private final Clock clock;
    private final int limit;
    private final int reserve;
    private final Duration window;
    private final Duration maxWait;
    private final LongConsumer sleeper;
    private final Deque<Instant> recent = new ArrayDeque<>();

    ItadRequestLimiter(Clock clock) { this(clock, DEFAULT_LIMIT, DEFAULT_RESERVE, DEFAULT_WINDOW, DEFAULT_MAX_WAIT, ItadRequestLimiter::sleepMillis); }

    ItadRequestLimiter(Clock clock, int limit, Duration window, Duration maxWait, LongConsumer sleeper) { this(clock, limit, 0, window, maxWait, sleeper); }

    ItadRequestLimiter(Clock clock, int limit, int reserve, Duration window, Duration maxWait, LongConsumer sleeper) {
        if (limit < 1 || reserve < 0 || reserve >= limit || window.isNegative() || window.isZero() || maxWait.isNegative()) throw new IllegalArgumentException("Geçersiz ITAD hız sınırı.");
        this.reserve = reserve;
        this.clock = clock;
        this.limit = limit;
        this.window = window;
        this.maxWait = maxWait;
        this.sleeper = sleeper;
    }

    /** Arka plan (toplu tur) isteği hakkı alır. */
    void acquire() { acquire(false); }

    /** Bir istek hakkı alır; pencere doluysa gerekirse bekler ya da {@code PRICE_LOCAL_THROTTLE} fırlatır. {@code interactive}: ayrılmış paydan da yararlanır. */
    void acquire(boolean interactive) {
        while (true) {
            Duration wait = tryAcquire(interactive);
            if (wait == null) return;
            if (wait.compareTo(maxWait) > 0) throw new ProviderException(LOCAL_THROTTLE, wait);
            sleeper.accept(wait.toMillis() + 1);
        }
    }

    /** @return hak alındıysa null; aksi halde bir sonraki hakka kadar beklenecek süre */
    private synchronized Duration tryAcquire(boolean interactive) {
        Instant now = clock.instant();
        while (!recent.isEmpty() && !recent.peekFirst().plus(window).isAfter(now)) recent.removeFirst();
        int allowed = interactive ? limit : limit - reserve;
        if (recent.size() < allowed) {
            recent.addLast(now);
            return null;
        }
        // Yer açılması için pencereden çıkması gereken en eski istek: (boyut - izin verilen). sıradaki.
        var oldest = recent.iterator();
        for (int skip = recent.size() - allowed; skip > 0; skip--) oldest.next();
        return Duration.between(now, oldest.next().plus(window));
    }

    /** Pencere doluluğunun anlık görüntüsü (yalnız gözlem: 429 anında kaç istek atıldığını loglamak için). */
    record Snapshot(int inWindow, int last60s, int last10s, int limit, int reserve) { }

    synchronized Snapshot snapshot() {
        Instant now = clock.instant();
        while (!recent.isEmpty() && !recent.peekFirst().plus(window).isAfter(now)) recent.removeFirst();
        int last60 = 0;
        int last10 = 0;
        for (Instant at : recent) {
            if (at.plusSeconds(60).isAfter(now)) last60++;
            if (at.plusSeconds(10).isAfter(now)) last10++;
        }
        return new Snapshot(recent.size(), last60, last10, limit, reserve);
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

package com.ltz.game.infrastructure.client;

import com.ltz.game.domain.exception.ProviderException;
import com.ltz.game.infrastructure.config.ItadRateProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.LongConsumer;

/**
 * ITAD istek sınırı için istemci tarafı kayan pencere sınırlayıcı. Pencere doluysa en eski isteğin pencereden çıkmasına kadar bekler; bekleme
 * üst sınırı aşacaksa {@code PRICE_LOCAL_THROTTLE} fırlatır (çağıran turu bırakır, oyunları başarısız saymaz).
 *
 * <p><b>Etkin limit.</b> Gerçek ITAD kotası belgelenmediği ve DEV anahtarı ekip tarafından paylaşıldığı için sabit bir sayıya güvenilmez (ölçüm: 5 dakikada
 * ≈160-220 istekte 429). {@code adaptive} açıkken, kendi trafiğimiz etkin limitin en az yarısına ulaşmışken gelen 429'da etkin limit o ana kadar atılan istek sayısının
 * %70'ine (en az {@code floor}) iner; küçük bir gözlemde (restart sonrası patlama, başka tüketici) gelen 429 limiti DÜŞÜRMEZ, yalnız {@code Retry-After} bloğu geçerlidir.
 * 429 olmadan geçen her {@code increaseInterval}'de {@code limit/10} artar ve yapılandırılmış {@code limit}'i aşmaz. Eşzamanlı 429'lar birikmez.
 *
 * <p><b>Ayrılmış pay.</b> Etkin limitin {@code reserve/limit} oranı sayfası açılan oyunlara (etkileşimli) ayrılmıştır: toplu arka plan turları bunun altındaki
 * paya, etkileşimli istekler etkin limitin tamamına ulaşabilir.
 */
final class ItadRequestLimiter {
    static final int DEFAULT_LIMIT = 850;
    /** Pencere payından sayfası açılan oyunlara ayrılan kısım (≈%18): 5 dakikada yüzlerce sayfa açılışına yeter. */
    static final int DEFAULT_RESERVE = 150;
    static final Duration DEFAULT_WINDOW = Duration.ofMinutes(5);
    static final Duration DEFAULT_MAX_WAIT = Duration.ofSeconds(20);
    /** Kendi sınırımız dolduğunda atılan kod: ITAD hatası değildir, çağıran bunu oyun başarısızlığı olarak KAYDETMEMELİ, yalnız turu bırakmalıdır. */
    static final String LOCAL_THROTTLE = "PRICE_LOCAL_THROTTLE";
    /** 429'da etkin limit, gözlenen istek sayısının bu oranına iner (kota payı bırakmak için). */
    private static final int DECREASE_PERCENT = 70;
    private final Clock clock;
    private final int limit;
    private final int reserve;
    private final int floor;
    private final boolean adaptive;
    private final Duration window;
    private final Duration maxWait;
    private final Duration interactiveMaxWait;
    private final Duration increaseInterval;
    private final LongConsumer sleeper;
    private final Deque<Instant> recent = new ArrayDeque<>();
    private int effective;
    private Instant lastChange;

    ItadRequestLimiter(Clock clock) { this(clock, DEFAULT_LIMIT, DEFAULT_RESERVE, DEFAULT_WINDOW, DEFAULT_MAX_WAIT, ItadRequestLimiter::sleepMillis); }

    ItadRequestLimiter(Clock clock, int limit, Duration window, Duration maxWait, LongConsumer sleeper) { this(clock, limit, 0, window, maxWait, sleeper); }

    /** Uyarlanmayan (sabit limitli) sınırlayıcı; testler ve basit kurulumlar için. */
    ItadRequestLimiter(Clock clock, int limit, int reserve, Duration window, Duration maxWait, LongConsumer sleeper) {
        this(clock, new Settings(limit, reserve, limit, false, window, maxWait, maxWait, Duration.ofMinutes(2)), sleeper);
    }

    /** Doğrulanmış sınırlayıcı ayarları. */
    private record Settings(int limit, int reserve, int floor, boolean adaptive, Duration window, Duration maxWait, Duration interactiveMaxWait, Duration increaseInterval) {
        Settings {
            if (limit < 1 || reserve < 0 || reserve >= limit || floor < 1 || floor > limit || window.isNegative() || window.isZero() || maxWait.isNegative()
                    || interactiveMaxWait.isNegative() || increaseInterval.isNegative() || increaseInterval.isZero()) throw new IllegalArgumentException("Geçersiz ITAD hız sınırı.");
        }
    }

    private ItadRequestLimiter(Clock clock, Settings settings, LongConsumer sleeper) {
        this.clock = clock;
        this.limit = settings.limit();
        this.reserve = settings.reserve();
        this.floor = settings.floor();
        this.adaptive = settings.adaptive();
        this.window = settings.window();
        this.maxWait = settings.maxWait();
        this.interactiveMaxWait = settings.interactiveMaxWait();
        this.increaseInterval = settings.increaseInterval();
        this.sleeper = sleeper;
        this.effective = limit;
        this.lastChange = clock.instant();
    }

    /** Yapılandırmadan kurar (uyarlamalı). */
    static ItadRequestLimiter from(ItadRateProperties rate, Clock clock) {
        return new ItadRequestLimiter(clock, new Settings(rate.limit(), rate.reserve(), rate.floor(), rate.adaptive(), rate.window(), rate.maxWait(), rate.interactiveMaxWait(),
                rate.increaseInterval()), ItadRequestLimiter::sleepMillis);
    }

    /** Arka plan (toplu tur) isteği hakkı alır. */
    void acquire() { acquire(false); }

    /** Bir istek hakkı alır; pencere doluysa gerekirse bekler ya da {@code PRICE_LOCAL_THROTTLE} fırlatır. {@code interactive}: ayrılmış paydan da yararlanır ve kısa bekler. */
    void acquire(boolean interactive) {
        Duration cap = interactive ? interactiveMaxWait : maxWait;
        while (true) {
            Duration wait = tryAcquire(interactive);
            if (wait == null) return;
            if (wait.compareTo(cap) > 0) throw new ProviderException(LOCAL_THROTTLE, wait);
            sleeper.accept(wait.toMillis() + 1);
        }
    }

    /** @return hak alındıysa null; aksi halde bir sonraki hakka kadar beklenecek süre */
    private synchronized Duration tryAcquire(boolean interactive) {
        Instant now = clock.instant();
        prune(now);
        grow(now);
        int allowed = interactive ? effective : Math.max(1, effective - reservedShare());
        if (recent.size() < allowed) {
            recent.addLast(now);
            return null;
        }
        // Yer açılması için pencereden çıkması gereken en eski istek: (boyut - izin verilen). sıradaki.
        var oldest = recent.iterator();
        for (int skip = recent.size() - allowed; skip > 0; skip--) oldest.next();
        return Duration.between(now, oldest.next().plus(window));
    }

    private void prune(Instant now) {
        while (!recent.isEmpty() && !recent.peekFirst().plus(window).isAfter(now)) recent.removeFirst();
    }

    /** 429 olmadan geçen her artış aralığında etkin limit {@code limit/10} artar (yapılandırılmış limiti aşmaz). */
    private void grow(Instant now) {
        if (!adaptive || effective >= limit || lastChange.plus(increaseInterval).isAfter(now)) return;
        effective = Math.min(limit, effective + Math.max(1, limit / 10));
        lastChange = now;
    }

    /** Etkin limitin ayrılmış payı: limit ile orantılı küçülür. */
    private int reservedShare() { return (int) Math.round(effective * (double) reserve / limit); }

    /**
     * ITAD 429 verdi. Gözlenen istek sayısı etkin limitin yarısına ulaştıysa etkin limit gözlenenin %70'ine (en az {@code floor}) iner; ulaşmadıysa 429 bizim hızımızdan
     * değil kısa bir patlamadan ya da başka bir tüketiciden gelmiştir ve limit değişmez. Aynı gözlemden hesaplandığı için eşzamanlı 429'lar birikmez.
     *
     * @return yeni etkin limit
     */
    synchronized int onRateLimited(int observedInWindow) {
        if (!adaptive || observedInWindow * 2 < effective) return effective;
        effective = Math.clamp(observedInWindow * DECREASE_PERCENT / 100, floor, effective);
        lastChange = clock.instant();
        return effective;
    }

    /** Pencere doluluğunun anlık görüntüsü (yalnız gözlem: 429 anında kaç istek atıldığını loglamak için). */
    record Snapshot(int inWindow, int last60s, int last10s, int limit, int reserve, int effective) { }

    synchronized Snapshot snapshot() {
        Instant now = clock.instant();
        prune(now);
        int last60 = 0;
        int last10 = 0;
        for (Instant at : recent) {
            if (at.plusSeconds(60).isAfter(now)) last60++;
            if (at.plusSeconds(10).isAfter(now)) last10++;
        }
        return new Snapshot(recent.size(), last60, last10, limit, reserve, effective);
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

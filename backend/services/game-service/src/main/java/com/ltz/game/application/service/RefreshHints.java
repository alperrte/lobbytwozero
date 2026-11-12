package com.ltz.game.application.service;

import com.ltz.game.application.port.GamePriceProvider.Market;
import com.ltz.game.infrastructure.config.GameRefreshProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Sayfası açılan oyunların bellek içi, sınırlı ipucu kuyruğu. Kullanıcı isteği dış servise HİÇ dokunmaz ve veritabanına yazmaz: istek yalnız bir
 * ipucu bırakır, yenilemeyi {@link RefreshHintDrain} kota sınırları içinde arka planda yapar. Sınırlar: aynı (oyun, pazar) tekrar penceresi içinde
 * yeniden kuyruğa girmez, kuyruk {@code maxKeys}'te dolar ve dolunca yeni ipucu atılır (kota ve bellek güvenliği, kullanıcıya hata dönmez).
 */
@Component
public class RefreshHints {
    /** Bir ipucu: oyun ve (yalnız fiyat için) pazar; pazarsız ipucu çıkış durumu ve detay yenilemesi içindir. */
    public record Key(UUID gameId, Market market) { }

    private final GameRefreshProperties properties;
    private final Clock clock;
    /** Geçici nedenle (kota, ITAD bloğu, kiralama) yapılamayan bir ipucunun en çok kaç kez yeniden kuyruğa alınacağı. */
    static final int MAX_REQUEUES = 2;
    private static final Duration MIN_DELAY = Duration.ofSeconds(10);
    private static final Duration MAX_DELAY = Duration.ofMinutes(5);
    /** Değer: ipucunun işlenebileceği en erken zaman (yeni ipucunda dokunma anı, yeniden kuyruğa alınanda gecikmeli). */
    private final ConcurrentHashMap<Key, Instant> pending = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Key, Instant> seen = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Key, Integer> retries = new ConcurrentHashMap<>();

    public RefreshHints(GameRefreshProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** Oyun sayfası açıldı ({@code market == null}) ya da bir pazarın fiyatı okundu. Hiçbir zaman istisna fırlatmaz ve beklemez. */
    public void touch(UUID gameId, Market market) {
        if (gameId == null || !properties.hintsEnabled()) return;
        Key key = new Key(gameId, market);
        Instant now = clock.instant();
        Instant last = seen.get(key);
        if (last != null && last.plus(properties.hints().repeatWindow()).isAfter(now)) return;
        if (pending.size() >= properties.hints().maxKeys()) return;
        if (seen.size() >= properties.hints().maxKeys() * 4L) prune(now);
        seen.put(key, now);
        if (pending.putIfAbsent(key, now) == null) retries.remove(key);
    }

    /**
     * Geçici nedenle yapılamayan ipucunu {@code delay} sonra yeniden kuyruğa alır (en çok {@value #MAX_REQUEUES} kez). Sınır aşılırsa ipucu bırakılır ve
     * "görüldü" kaydı silinir, böylece kullanıcının sonraki sayfa açılışı 5 dakika beklemeden yeniden ipucu bırakabilir.
     *
     * @return yeniden kuyruğa alındıysa true
     */
    public boolean requeue(Key key, Duration delay) {
        if (key == null || !properties.hintsEnabled()) return false;
        if (retries.merge(key, 1, Integer::sum) > MAX_REQUEUES) {
            retries.remove(key);
            seen.remove(key);
            return false;
        }
        if (pending.size() >= properties.hints().maxKeys()) return false;
        pending.put(key, clock.instant().plus(boundedDelay(delay)));
        return true;
    }

    /** Yeniden deneme gecikmesi en az {@link #MIN_DELAY}, en çok {@link #MAX_DELAY}; bilinmiyorsa en az. */
    private static Duration boundedDelay(Duration delay) {
        if (delay == null || delay.compareTo(MIN_DELAY) < 0) return MIN_DELAY;
        return delay.compareTo(MAX_DELAY) > 0 ? MAX_DELAY : delay;
    }

    /** En çok {@code max} ipucu alır ve kuyruktan çıkarır. */
    public List<Key> drain(int max) {
        List<Key> taken = new ArrayList<>();
        Instant now = clock.instant();
        for (var entry : pending.entrySet()) {
            if (taken.size() >= max) break;
            if (!entry.getValue().isAfter(now) && pending.remove(entry.getKey(), entry.getValue())) taken.add(entry.getKey());
        }
        return taken;
    }

    /** Bekleyen ipucu sayısı (testler ve izleme için). */
    public int size() { return pending.size(); }

    private void prune(Instant now) {
        Instant cutoff = now.minus(properties.hints().repeatWindow());
        seen.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
        if (seen.size() >= properties.hints().maxKeys() * 4L) seen.clear();
    }
}

package com.ltz.game.application.service;

import com.ltz.game.application.port.GamePriceProvider.Market;
import com.ltz.game.infrastructure.config.GameRefreshProperties;
import java.time.Clock;
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
    private final ConcurrentHashMap<Key, Instant> pending = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Key, Instant> seen = new ConcurrentHashMap<>();

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
        pending.putIfAbsent(key, now);
    }

    /** En çok {@code max} ipucu alır ve kuyruktan çıkarır. */
    public List<Key> drain(int max) {
        List<Key> taken = new ArrayList<>();
        for (Key key : pending.keySet()) {
            if (taken.size() >= max) break;
            if (pending.remove(key) != null) taken.add(key);
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

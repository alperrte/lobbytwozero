package com.ltz.game.application.service;

import com.ltz.game.application.port.GamePriceProvider.Market;
import com.ltz.game.application.port.GameProvider;
import com.ltz.game.application.port.ProviderPriority;
import com.ltz.game.infrastructure.config.GameRefreshProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Sayfası açılan oyunlar için anlık yenileme: {@link RefreshHints} kuyruğunu kota sınırları içinde boşaltır. Tahliye başına en çok {@code batch}
 * oyun ve dakikada en çok {@code maxDrainsPerMinute} tahliye yapılır (Steam/IGDB/ITAD çağrı üst sınırı). Pazarsız ipucu çıkış durumunu (Steam)
 * ve detayı (IGDB), pazarlı ipucu o pazarın fiyatını (ITAD + Steam) yeniler. Yenileme en iyi çabadır: sağlayıcı duraklamada, kiralama başka
 * yerde ya da oyun yenilenemezse ipucu atılır; planlı döngü zaten oyunu kademesinde yeniler.
 */
@Service
public class RefreshHintDrain {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(RefreshHintDrain.class);
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final RefreshHints hints;
    private final SteamReleaseStatusService status;
    private final GameDetailBulkService details;
    private final GamePriceService prices;
    private final GameProvider provider;
    private final GameRefreshProperties properties;
    private final Clock clock;
    private final Deque<Instant> recentDrains = new ArrayDeque<>();

    public RefreshHintDrain(RefreshHints hints, SteamReleaseStatusService status, GameDetailBulkService details, GamePriceService prices,
            GameProvider provider, GameRefreshProperties properties, Clock clock) {
        this.hints = hints;
        this.status = status;
        this.details = details;
        this.prices = prices;
        this.provider = provider;
        this.properties = properties;
        this.clock = clock;
    }

    /** Bir pazarın fiyat ipuçlarını yeniler. Geçici nedenle (kota, ITAD bloğu, kiralama) yapılamayanlar sınırlı sayıda yeniden kuyruğa alınır; bir pazarın hatası diğerlerini kaybettirmez. */
    private void refreshPrices(Market market, Set<UUID> ids) {
        try {
            RefreshNowResult result = prices.refreshNowResult(ids, market);
            if (result != null && result.retryable()) ids.forEach(id -> hints.requeue(new RefreshHints.Key(id, market), result.retryAfter()));
        } catch (RuntimeException error) {
            LOG.warn("Fiyat ipucu yenilemesi başarısız: pazar={}-{} hata={}", market.country(), market.currency(), error.getClass().getSimpleName());
        }
    }

    /** @return bu tahliyede işlenen ipucu sayısı; sınır doluysa veya kuyruk boşsa {@code 0} */
    public synchronized int drain() {
        if (!properties.hintsEnabled()) return 0;
        Instant now = clock.instant();
        while (!recentDrains.isEmpty() && recentDrains.peekFirst().plus(WINDOW).isBefore(now)) recentDrains.removeFirst();
        if (recentDrains.size() >= properties.hints().maxDrainsPerMinute()) return 0;
        var keys = hints.drain(properties.hints().batch());
        if (keys.isEmpty()) return 0;
        recentDrains.addLast(now);
        Set<UUID> viewed = new LinkedHashSet<>();
        Map<Market, Set<UUID>> priced = new LinkedHashMap<>();
        for (var key : keys) {
            if (key.market() == null) viewed.add(key.gameId());
            else priced.computeIfAbsent(key.market(), market -> new HashSet<>()).add(key.gameId());
        }
        // Sayfası açılan oyun öncelikli işlenir: arka plan kotası dolsa bile günlük sınırın sonuna kadar yenilenir.
        ProviderPriority.essential(() -> {
            if (!viewed.isEmpty()) {
                if (provider.storePricesEnabled()) status.refreshNow(viewed);
                details.refreshNow(viewed);
            }
            priced.forEach(this::refreshPrices);
            return Boolean.TRUE;
        });
        return keys.size();
    }
}

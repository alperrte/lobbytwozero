package com.ltz.game.application.service;

import com.ltz.game.application.port.GamePriceProvider.Market;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;

/**
 * Fiyat yenileme turunda hangi pazarların, hangi sırayla çalışacağını seçer.
 *
 * <ul>
 *   <li><b>En az işlenen önce:</b> süre dolan turda listenin sonundaki pazarlar aç kalmasın. (Her tur sırayı bir kaydırmak yetmedi: ITAD engeli
 *       yüzünden üretken turlar belirli fazlara denk gelip aynı pazarları sürekli sona bırakıyordu; DEV'de KR/JP/BR diğer pazarların yaklaşık
 *       dörtte biri kadar yenilendi.)</li>
 *   <li><b>Birikimli pazar önce ve yalnız o:</b> hiç fiyatı olmayan oyun birikimi olan pazar (ilk dolum, yeni pazar) varsa tur yalnız ona ayrılır;
 *       sağlayıcı kotası ona yetsin, diğer pazarların yenilemesi kotayı tüketip ilk dolumu yavaşlatmasın (DEV'de TR dolarken 12 pazarın yenilemesi
 *       kotanın çoğunu yiyordu). Birikimli pazar sürekli hata verip diğerlerini aç bırakmasın diye her {@value #FULL_ROUND_EVERY}. turda tüm pazarlar
 *       sırayla çalışır.</li>
 * </ul>
 */
final class PriceMarketScheduler {
    /** Birikim varken her bu kadar turda bir tüm pazarlara sıra gelir. */
    static final int FULL_ROUND_EVERY = 5;

    /** Pazar başına bu süreçte şimdiye kadar yenilenen oyun sayısı. */
    private final Map<Market, AtomicLong> served = new ConcurrentHashMap<>();
    private final AtomicInteger runs = new AtomicInteger();

    long servedCount(Market market) {
        var count = served.get(market);
        return count == null ? 0 : count.get();
    }

    void served(Market market, int refreshed) {
        if (refreshed > 0) served.computeIfAbsent(market, key -> new AtomicLong()).addAndGet(refreshed);
    }

    /** Bu turda yenilenecek pazarlar (sıralı). */
    List<Market> runMarkets(List<Market> markets, Predicate<Market> inBacklog) {
        List<Market> order = leastServedFirst(markets, market -> inBacklog.test(market) ? -1 : servedCount(market));
        List<Market> backlog = order.stream().filter(inBacklog).toList();
        boolean fullRound = runs.getAndIncrement() % FULL_ROUND_EVERY == FULL_ROUND_EVERY - 1;
        return backlog.isEmpty() || fullRound ? order : backlog;
    }

    /** En az işlenen önce; eşitlikte yapılandırma sırası korunur. */
    static <T> List<T> leastServedFirst(List<T> items, ToLongFunction<T> servedCount) {
        List<T> copy = new ArrayList<>(items);
        copy.sort(Comparator.comparingLong(servedCount));
        return copy;
    }
}

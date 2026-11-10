package com.ltz.game.infrastructure.client;

import com.ltz.game.application.port.GameProvider;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/**
 * Steam {@code IStoreBrowseService/GetItems} yanıtından mağaza fiyatı ayrıştırması (durumsuz). İstek gövdesi ve HTTP {@link SteamStoreBrowse} ve
 * {@link SteamGameProvider}'dadır; yanıt güvenilmez girdi sayılır ve sağlayıcının vermediği değer uydurulmaz.
 */
final class SteamStorePrices {
    /** Bir tutarın en çok hane sayısı ve en büyük epoch saniye (yıl 2100). */
    private static final long MAX_EPOCH_SECONDS = 4_102_444_800L;

    private SteamStorePrices() { }

    /**
     * Bir oyunun fiyatı: paket (bundle) olmayan satın alma seçenekleri içinde en düşük güncel fiyat (aynı kural ITAD'de "mağaza başına en düşük"
     * teklifidir). Tutarlar küçük para birimi birimindedir ve tüm pazarlarda (JPY/KRW dahil) yüzde biri olarak gelir. {@code original_price_in_cents}
     * ve {@code discount_pct} yalnız indirimde gelir. Fiyatı olmayan (ücretsiz, çıkmamış, o ülkede satılmayan) oyun sonuçta yer almaz.
     */
    static Map<String, GameProvider.StorePrice> parse(List<String> requested, byte[] body) {
        Map<String, GameProvider.StorePrice> result = new LinkedHashMap<>();
        for (JsonNode item : SteamStoreBrowse.storeItemNodes(body)) {
            String appId = SteamStoreBrowse.appId(item, requested);
            GameProvider.StorePrice best = appId == null ? null : bestPrice(item);
            if (best != null) {
                result.put(appId, best);
            }
        }
        return result;
    }

    /** Seçenek listesi yoksa Steam'in {@code best_purchase_option} değerine düşer. */
    private static GameProvider.StorePrice bestPrice(JsonNode item) {
        JsonNode options = item.path("purchase_options");
        if (!options.isArray()) {
            return optionPrice(item.path("best_purchase_option"));
        }
        GameProvider.StorePrice best = null;
        for (JsonNode option : options) {
            GameProvider.StorePrice price = optionPrice(option);
            if (price != null && (best == null || price.finalCents() < best.finalCents())) {
                best = price;
            }
        }
        return best;
    }

    private static GameProvider.StorePrice optionPrice(JsonNode option) {
        if (!option.isObject() || !option.hasNonNull("packageid") || option.hasNonNull("bundleid") || option.path("must_purchase_as_set").asBoolean(false)) {
            return null;
        }
        Long finalCents = cents(option.get("final_price_in_cents"));
        if (finalCents == null) {
            return null;
        }
        Long originalCents = cents(option.get("original_price_in_cents"));
        long original = originalCents == null || originalCents < finalCents ? finalCents : originalCents;
        int percent = option.path("discount_pct").asInt(-1);
        if (percent < 0 || percent > 100) {
            percent = original > finalCents ? (int) Math.round((1.0 - (double) finalCents / original) * 100) : 0;
        }
        Instant saleEnd = percent > 0 ? saleEnd(option.path("active_discounts")) : null;
        return new GameProvider.StorePrice(finalCents, original, percent, saleEnd);
    }

    /** En yakın indirim bitişi (epoch saniye); yoksa veya geçersizse null. */
    private static Instant saleEnd(JsonNode discounts) {
        long earliest = Long.MAX_VALUE;
        if (discounts.isArray()) {
            for (JsonNode discount : discounts) {
                long end = discount.path("discount_end_date").asLong(0);
                if (end > 0 && end < earliest) {
                    earliest = end;
                }
            }
        }
        return earliest == Long.MAX_VALUE || earliest > MAX_EPOCH_SECONDS ? null : Instant.ofEpochSecond(earliest);
    }

    /** Steam tutarı metin ("1499") veya sayı olarak gönderir; yalnız en çok 10 haneli, negatif olmayan tam sayı kabul edilir. */
    private static Long cents(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.isString() ? value.asString().strip() : value.isIntegralNumber() ? Long.toString(value.asLong()) : "";
        return text.matches("\\d{1,10}") ? Long.valueOf(text) : null;
    }
}

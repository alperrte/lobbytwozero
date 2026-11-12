package com.ltz.game.infrastructure.client;

import com.ltz.game.application.port.GamePriceProvider.Market;
import com.ltz.game.application.port.GamePriceProvider.Quote;
import com.ltz.game.domain.exception.ProviderException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import static com.ltz.game.infrastructure.client.ItadPriceValues.invalid;
import static com.ltz.game.infrastructure.client.ItadPriceValues.text;

/**
 * Yanıt tutarlı olmalıdır (tekil oyun/mağaza, geçerli para birimi, tarih, bağlantı). ITAD fiyatı olmayan oyun için satır
 * döndürmez: istenen ama satırı olmayan oyun "teklif yok" demektir ve yalnız o oyun için teklif üretilmez; tek bir
 * teklifsiz oyun, aynı istekteki diğer oyunların fiyatını geçersiz kılmaz.
 */
final class ItadPriceMapper {
    /** Bir oyun için kabul edilen en çok teklif (mağaza) sayısı: ITAD'in gerçek en yüksek değeri 21'dir; bozuk/şişirilmiş yanıt bunun üstünde reddedilir. */
    static final int MAX_DEALS = 64;
    private ItadPriceMapper() { }
    static List<Quote> quotes(JsonNode rows,Map<UUID,List<UUID>> matched,Market market,Instant now,Duration ttl) {
        if(rows==null || !rows.isArray() || rows.size()>matched.size()) throw invalid();
        List<Quote> quotes=new ArrayList<>();
        Set<UUID> seen=new HashSet<>();
        for(JsonNode row:rows) {
            UUID external=ItadPriceValues.uuid(text(row,"id"));
            if(!matched.containsKey(external) || !seen.add(external)) throw invalid();
            append(row,matched.get(external),market,now,ttl,quotes);
        }
        return List.copyOf(quotes);
    }
    /**
     * Bir oyunun tekliflerini mağaza başına tek teklife indirir. ITAD aynı mağaza için birden çok teklif döndürebilir (ör. iki
     * Steam paketi/sürümü): en düşük güncel fiyat korunur, eşitlikte ilk teklif. Süresi (expiry) geçmiş teklif güncel fiyat
     * değildir ve atlanır; tek bir bayat veya yinelenen teklif aynı istekteki diğer oyunların fiyatını geçersiz kılmaz.
     */
    private static void append(JsonNode row,List<UUID> games,Market market,Instant now,Duration ttl,List<Quote> quotes) {
        java.util.Map<String,JsonNode> bestByStore=new java.util.LinkedHashMap<>();
        for(JsonNode deal:deals(row)) {
            String store=text(deal.get("shop"),"name");
            if(store.length()>64) throw invalid();
            if(expired(deal,now)) continue;
            JsonNode known=bestByStore.get(store);
            if(known==null || ItadPriceValues.amount(deal.get("price")).compareTo(ItadPriceValues.amount(known.get("price")))<0) bestByStore.put(store,deal);
        }
        for(var entry:bestByStore.entrySet())
            for(UUID id:games) quotes.add(quote(id,entry.getKey(),entry.getValue(),market,now,ttl));
    }
    private static boolean expired(JsonNode deal,Instant now) {
        JsonNode expiry=deal.get("expiry");
        return expiry!=null && !expiry.isNull() && !ItadPriceValues.instant(expiry.asText()).isAfter(now);
    }
    private static JsonNode deals(JsonNode row) {
        JsonNode deals=row.get("deals");
        if(deals==null || !deals.isArray() || deals.size()>MAX_DEALS) throw invalid();
        return deals;
    }
    private static Quote quote(UUID id,String store,JsonNode deal,Market market,Instant now,Duration ttl) {
        JsonNode price=deal.get("price");
        JsonNode regular=deal.get("regular");
        String currency=text(price,"currency");
        if(!currency.equals(text(regular,"currency"))) throw invalid();
        if(!market.currency().equals(currency)) throw new ProviderException("PRICE_CURRENCY_UNAVAILABLE");
        var current=ItadPriceValues.amount(price);
        var normal=ItadPriceValues.amount(regular);
        if(current.compareTo(normal)>0) throw invalid();
        Instant updated=ItadPriceValues.instant(text(deal,"timestamp"));
        if(updated.isAfter(now.plusSeconds(30))) throw invalid();
        return new Quote(id,store,market,normal,current,"ITAD",now,ItadPriceValues.expires(deal,now,ttl),
                ItadPriceValues.link(text(deal,"url")),ItadPriceValues.percent(deal),updated);
    }
}

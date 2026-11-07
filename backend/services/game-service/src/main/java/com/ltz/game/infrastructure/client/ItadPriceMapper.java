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

/** Tam ve tekil oyun/mağaza yanıtı gerekir; eksik yanıt önceki snapshot'ı silemez. */
final class ItadPriceMapper {
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
        if(!seen.equals(matched.keySet())) throw invalid();
        return List.copyOf(quotes);
    }
    private static void append(JsonNode row,List<UUID> games,Market market,Instant now,Duration ttl,List<Quote> quotes) {
        Set<String> shops=new HashSet<>();
        for(JsonNode deal:deals(row)) {
            String store=text(deal.get("shop"),"name");
            if(store.length()>64 || !shops.add(store)) throw invalid();
            for(UUID id:games) quotes.add(quote(id,store,deal,market,now,ttl));
        }
    }
    private static JsonNode deals(JsonNode row) {
        JsonNode deals=row.get("deals");
        if(deals==null || !deals.isArray() || deals.size()>5) throw invalid();
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

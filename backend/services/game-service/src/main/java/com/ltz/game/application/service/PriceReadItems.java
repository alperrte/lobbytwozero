package com.ltz.game.application.service;

import com.ltz.game.api.dto.response.GamePricesResponse.Item;
import com.ltz.game.application.port.GamePriceProvider.Quote;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Fiyat okumasında bir oyunun durumunu belirler. {@code AVAILABLE}: en az bir güncel teklif (yalnız güncel olanlar); {@code STALE}: yalnız süresi geçmiş teklifler
 * (son bilinen teklifler döner, güncel fiyat sayılmaz); {@code NO_OFFER}: teklif yok ama yakın zamanda başarılı kontrol var; {@code UNAVAILABLE}: veri yok veya oyun aktif değil.
 */
final class PriceReadItems {
    private PriceReadItems() { }

    static Item item(UUID id, Set<UUID> active, Map<UUID, List<Quote>> byGame, Set<UUID> noOffers, Instant now) {
        if (!active.contains(id)) return new Item(id, "UNAVAILABLE", List.of());
        List<Quote> quotes = byGame.getOrDefault(id, List.of());
        if (quotes.isEmpty()) return new Item(id, noOffers.contains(id) ? "NO_OFFER" : "UNAVAILABLE", List.of());
        List<Quote> fresh = quotes.stream().filter(quote -> quote.isFreshAt(now)).toList();
        return fresh.isEmpty() ? new Item(id, "STALE", quotes) : new Item(id, "AVAILABLE", fresh);
    }
}

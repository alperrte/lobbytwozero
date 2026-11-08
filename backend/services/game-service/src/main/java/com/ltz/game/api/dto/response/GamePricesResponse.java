package com.ltz.game.api.dto.response;

import com.ltz.game.application.port.GamePriceProvider.Market;
import com.ltz.game.application.port.GamePriceProvider.Quote;
import java.util.List;
import java.util.UUID;

/** Yalnız belirtilen pazarın snapshot'ı; STALE güncel fiyat iddiası değildir. */
public record GamePricesResponse(Market market,List<Item> items) {
    public record Item(UUID gameId,String status,List<Quote> offers) { }
}

package com.ltz.game.application.service;
import com.ltz.game.application.port.GamePriceProvider.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GamePriceContractTest {
    final UUID id=UUID.randomUUID();
    final Market market=new Market("TR","USD");
    final Instant fetched=Instant.parse("2026-10-21T00:00:00Z");
    @Test void regionalCurrencyAndFreshnessAreExplicitAndExpiryIsExclusive() {
        var quote=new Quote(id,"SYNTHETIC_STORE",market,new BigDecimal("20.00"),new BigDecimal("10.00"),"TEST",fetched,fetched.plusSeconds(60));
        assertEquals("TR",quote.market().country()); assertEquals("USD",quote.market().currency());
        assertTrue(quote.isDiscounted()); assertTrue(quote.isFreshAt(fetched));
        assertFalse(quote.isFreshAt(fetched.minusSeconds(1))); assertFalse(quote.isFreshAt(fetched.plusSeconds(60)));
    }
    @Test void unknownCountryCurrencyAndMissingSourceAreRejected() {
        assertThrows(IllegalArgumentException.class,()->new Market("ZZ","USD"));
        assertThrows(IllegalArgumentException.class,()->new Market("TR","not-a-currency"));
        var expires=fetched.plusSeconds(1);
        assertThrows(IllegalArgumentException.class,()->new Quote(id,"Store",market,BigDecimal.ONE,BigDecimal.ZERO,null,fetched,expires));
    }
    @Test void freeIsNotMissingAndInvalidOrStaleValuesAreNotCurrent() {
        var free=new Quote(id,"Store",market,BigDecimal.ZERO,BigDecimal.ZERO,"TEST",fetched,fetched.plusSeconds(1));
        assertFalse(free.isDiscounted()); assertEquals(0,free.currentPrice().signum());
        var expires=fetched.plusSeconds(1);
        assertThrows(IllegalArgumentException.class,()->new Quote(id,"Store",market,BigDecimal.ZERO,BigDecimal.ONE,"TEST",fetched,expires));
        assertThrows(IllegalArgumentException.class,()->new Quote(id,"Store",market,BigDecimal.ONE,BigDecimal.ZERO,"TEST",fetched,fetched));
    }
}

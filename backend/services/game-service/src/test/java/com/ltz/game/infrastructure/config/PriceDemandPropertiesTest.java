package com.ltz.game.infrastructure.config;

import com.ltz.game.application.port.GamePriceProvider.Market;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PriceDemandPropertiesTest {
    @Test void defaultsKeepUsAndTrAlwaysActiveAndBoundTheDemandDrivenMarkets() {
        var defaults = PriceDemandProperties.defaults();
        assertTrue(defaults.enabled());
        assertEquals(List.of(new Market("US", "USD"), new Market("TR", "TRY")), defaults.always());
        assertEquals(Duration.ofDays(7), defaults.window());
        assertEquals(3, defaults.minReads());
        assertEquals(3, defaults.maxSecondary());
    }

    private static final Duration WEEK = Duration.ofDays(7);

    @Test void theAlwaysListIsValidatedDeduplicatedAndMayBeEmpty() {
        assertEquals(List.of(new Market("US", "USD")), new PriceDemandProperties(true, " US:USD , US:USD ", WEEK, 3, 3).always());
        assertEquals(List.of(), new PriceDemandProperties(true, "", WEEK, 3, 3).always());
        assertEquals(List.of(), new PriceDemandProperties(true, null, WEEK, 3, 3).always());
        assertThrows(IllegalArgumentException.class, () -> new PriceDemandProperties(true, "US", WEEK, 3, 3));
        assertThrows(IllegalArgumentException.class, () -> new PriceDemandProperties(true, "us:usd", WEEK, 3, 3));
    }

    @Test void invalidLimitsAreRejected() {
        Duration tooShort = Duration.ofHours(23);
        Duration tooLong = Duration.ofDays(31);
        assertThrows(IllegalArgumentException.class, () -> new PriceDemandProperties(true, "US:USD", tooShort, 3, 3));
        assertThrows(IllegalArgumentException.class, () -> new PriceDemandProperties(true, "US:USD", tooLong, 3, 3));
        assertThrows(IllegalArgumentException.class, () -> new PriceDemandProperties(true, "US:USD", null, 3, 3));
        assertThrows(IllegalArgumentException.class, () -> new PriceDemandProperties(true, "US:USD", WEEK, 0, 3));
        assertThrows(IllegalArgumentException.class, () -> new PriceDemandProperties(true, "US:USD", WEEK, 3, -1));
        assertThrows(IllegalArgumentException.class, () -> new PriceDemandProperties(true, "US:USD", WEEK, 3, 13));
    }
}

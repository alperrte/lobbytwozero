package com.ltz.game.infrastructure.client;

import com.ltz.game.domain.exception.ProviderException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SteamRequestBudgetTest {
    @Test void dailyQuotaAndNextUtcDay() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-20T00:00:00Z"));
        SteamRequestBudget budget = new SteamRequestBudget(clock);
        for (int i=0; i<50000; i++) {
            budget.acquire();
            clock.now = clock.now.plusSeconds(1);
        }
        assertEquals("RATE_LIMITED", assertThrows(ProviderException.class, budget::acquire).code());
        clock.now = Instant.parse("2026-10-21T00:00:00Z");
        assertDoesNotThrow(budget::acquire);
        assertThrows(ProviderException.class, budget::acquire);
    }
    private static class MutableClock extends Clock {
        private Instant now;
        MutableClock(Instant now) { this.now=now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}

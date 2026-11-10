package com.ltz.game.infrastructure.client;

import com.ltz.game.application.port.ProviderPriority;
import com.ltz.game.domain.exception.ProviderException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SteamRequestBudgetTest {
    @Test void backgroundWorkStopsAtThreeQuartersWhileEssentialWorkContinuesToTheDailyLimitAndTheNextUtcDayResets() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-20T00:00:00Z"));
        SteamRequestBudget budget = new SteamRequestBudget(clock);
        int background = (int) (50000 * SteamRequestBudget.BACKGROUND_SHARE);
        for (int i = 0; i < background; i++) {
            assertTrue(budget.backgroundAllowed());
            budget.acquire();
            clock.now = clock.now.plusSeconds(1);
        }
        assertFalse(budget.backgroundAllowed(), "arka plan payı doldu");
        ProviderException stopped = assertThrows(ProviderException.class, budget::acquire);
        assertEquals("RATE_LIMITED", stopped.code());
        assertTrue(stopped.retryAfter().toSeconds() > 0, "bir sonraki UTC güne kadar beklenir");
        for (int i = background; i < 50000; i++) {
            ProviderPriority.essential(() -> { budget.acquire(); return Boolean.TRUE; });
            clock.now = clock.now.plusSeconds(1);
        }
        assertEquals("RATE_LIMITED", assertThrows(ProviderException.class, () -> ProviderPriority.essential(() -> { budget.acquire(); return Boolean.TRUE; })).code(),
                "öncelikli iş de günlük sınırı aşamaz");
        assertFalse(ProviderPriority.isEssential(), "öncelik işaretti iş bitince geri alınır");
        clock.now = Instant.parse("2026-10-21T00:00:00Z");
        assertTrue(budget.backgroundAllowed());
        assertDoesNotThrow(budget::acquire);
    }
    @Test void theEssentialMarkIsRestoredEvenWhenTheWorkFails() {
        assertThrows(IllegalStateException.class, () -> ProviderPriority.essential(() -> { assertTrue(ProviderPriority.isEssential()); throw new IllegalStateException("x"); }));
        assertFalse(ProviderPriority.isEssential());
        assertEquals("inner", ProviderPriority.essential(() -> ProviderPriority.essential(() -> "inner")));
        assertFalse(ProviderPriority.isEssential());
    }
    private static class MutableClock extends Clock {
        private Instant now;
        MutableClock(Instant now) { this.now=now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}

package com.ltz.game.infrastructure.client;

import com.ltz.game.domain.exception.ProviderException;
import java.time.Clock;
import java.time.LocalDate;

/** Tek servis instance'ı için ihtiyatlı sınır: saniyede 1, UTC gününde 50.000 çağrı.
 * Çok instance ve paylaşılan API anahtarı için dağıtık kota gerekir.
 */
public class SteamRequestBudget {
    private final Clock clock;
    private LocalDate day;
    private long nextAllowedMillis;
    private int calls;
    public SteamRequestBudget(Clock clock) { this.clock = clock; }
    public synchronized void acquire() {
        LocalDate today = LocalDate.now(clock);
        if (!today.equals(day)) { day = today; calls = 0; }
        long now = clock.millis();
        if (calls >= 50000) throw new ProviderException("RATE_LIMITED",
                java.time.Duration.between(clock.instant(), today.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant()));
        if (now < nextAllowedMillis) throw new ProviderException("RATE_LIMITED", java.time.Duration.ofMillis(nextAllowedMillis - now));
        calls++;
        nextAllowedMillis = now + 1000;
    }
}

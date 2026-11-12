package com.ltz.game.infrastructure.client;

import com.ltz.game.application.port.ProviderPriority;
import com.ltz.game.domain.exception.ProviderException;
import java.time.Clock;
import java.time.LocalDate;

/**
 * Tek servis instance'ı için ihtiyatlı sınır: iki çağrı arasında en az {@code minIntervalMillis} ve UTC gününde en çok {@code dailyLimit} çağrı.
 * Varsayılan (geliştirme) saniyede 1 ve günde 50.000'dir; canlı profil bunu {@code ltz.game.steam.*} ile yükseltir. Steam'in anahtar başına
 * resmî sınırı günde 100.000 çağrıdır; çok instance ve paylaşılan API anahtarı için dağıtık kota gerekir, bu yüzden günlük sınır bilerek bunun altında tutulur.
 */
public class SteamRequestBudget {
    private static final long DEFAULT_MIN_INTERVAL_MILLIS = 1000;
    private static final int DEFAULT_DAILY_LIMIT = 50000;
    /** Arka plan taramalarının günlük kotadan kullanabileceği pay; kalan pay öncelikli çağrılar ({@link ProviderPriority}) içindir. */
    static final double BACKGROUND_SHARE = 0.75;
    private final Clock clock;
    private final long minIntervalMillis;
    private final int dailyLimit;
    private LocalDate day;
    private long nextAllowedMillis;
    private int calls;
    public SteamRequestBudget(Clock clock) { this(clock, DEFAULT_MIN_INTERVAL_MILLIS, DEFAULT_DAILY_LIMIT); }
    public SteamRequestBudget(Clock clock, long minIntervalMillis, int dailyLimit) {
        if (minIntervalMillis < 100 || dailyLimit < 1 || dailyLimit > 100000) throw new IllegalArgumentException("Geçersiz Steam istek bütçesi.");
        this.clock = clock;
        this.minIntervalMillis = minIntervalMillis;
        this.dailyLimit = dailyLimit;
    }
    /** Arka plan taramaları bugünkü payın altında mı (öncelikli çağrılar bu sınırdan etkilenmez). */
    public synchronized boolean backgroundAllowed() {
        resetDay();
        return calls < backgroundLimit();
    }

    private int backgroundLimit() { return (int) (dailyLimit * BACKGROUND_SHARE); }

    private LocalDate resetDay() {
        LocalDate today = LocalDate.now(clock);
        if (!today.equals(day)) { day = today; calls = 0; }
        return today;
    }

    public synchronized void acquire() {
        LocalDate today = resetDay();
        long now = clock.millis();
        if (calls >= dailyLimit || (calls >= backgroundLimit() && !ProviderPriority.isEssential())) throw new ProviderException("RATE_LIMITED",
                java.time.Duration.between(clock.instant(), today.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant()));
        if (now < nextAllowedMillis) throw new ProviderException("RATE_LIMITED", java.time.Duration.ofMillis(nextAllowedMillis - now));
        calls++;
        nextAllowedMillis = now + minIntervalMillis;
    }
}

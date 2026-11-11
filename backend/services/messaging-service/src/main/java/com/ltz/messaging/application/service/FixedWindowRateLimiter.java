package com.ltz.messaging.application.service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.function.IntSupplier;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Kullanıcı başına dakikalık sabit pencere sayacı (SECURITY.md §12) — kalıcı kayıt üretmeyen işlemler için (GIF arama,
 * tepki değişikliği). Sayaç DB'dedir ({@code rate_limit_windows}): birden çok örnek aynı sınırı paylaşır. Sayma tek
 * atomik SQL'dir; sınır doluysa satır değişmez ve işlem reddedilir. Çağıranın transaction'ı varsa ona katılır (işlem
 * geri alınırsa sayım da alınır). Eski pencereler {@link #evictExpired()} ile silinir.
 */
public final class FixedWindowRateLimiter {

    private static final long WINDOW_MILLIS = 60_000;

    private final String bucket;
    private final IntSupplier limit;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public FixedWindowRateLimiter(String bucket, IntSupplier limit, JdbcTemplate jdbc, Clock clock) {
        this.bucket = bucket;
        this.limit = limit;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Bu dakikadaki işlemi sayar; sınır aşıldıysa {@code false} döner ve sayılmaz. */
    public boolean tryAcquire(UUID userId) {
        return !jdbc.queryForList("""
                insert into rate_limit_windows (user_id, bucket, window_start, hits) values (?, ?, ?, 1)
                on conflict (user_id, bucket, window_start)
                do update set hits = rate_limit_windows.hits + 1 where rate_limit_windows.hits < ?
                returning hits""", Integer.class, userId, bucket, currentWindow(), limit.getAsInt()).isEmpty();
    }

    /** Geçmiş pencereleri siler (her örnek çalıştırabilir; idempotent). */
    public void evictExpired() {
        jdbc.update("delete from rate_limit_windows where bucket = ? and window_start < ?", bucket, currentWindow());
    }

    private Timestamp currentWindow() {
        return Timestamp.from(Instant.ofEpochMilli(clock.millis() / WINDOW_MILLIS * WINDOW_MILLIS));
    }
}

package com.ltz.performance.infrastructure.persistence.repository;

import com.ltz.performance.domain.entity.PerformanceDigest;
import com.ltz.performance.domain.exception.PerformanceRateLimitException;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Veritabanı kovası tabanlı dakikalık sınırlar. Çağıranlar bunu kendi işlem dışında kullanır; böylece
 * reddedilen/başarısız istekler de sayılır ve ek bağlantı gerekmez.
 */
@Repository
public class PerformanceWriteLimits {
    static final int CONSENT_OR_LINK_PER_USER = 30;
    static final int SYNC_PER_USER = 60;
    static final int SYNC_PER_DEVICE = 30;
    static final int SYNC_PER_PEER = 120;

    private final JdbcTemplate jdbc;

    public PerformanceWriteLimits(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Onay ve cihaz bağlama/devre dışı bırakma işlemleri. */
    public void link(UUID userId, String peer) {
        acquire("link-user:" + userId, CONSENT_OR_LINK_PER_USER);
        peer(peer);
    }

    public void sync(UUID userId, UUID deviceId, String peer) {
        acquire("sync-user:" + userId, SYNC_PER_USER);
        acquire("sync-device:" + userId + ":" + deviceId, SYNC_PER_DEVICE);
        peer(peer);
    }

    private void peer(String peer) {
        if (peer == null || peer.isBlank() || peer.length() > 128) throw new IllegalArgumentException("Geçersiz bağlantı");
        acquire("sync-peer:" + PerformanceDigest.hash(peer), SYNC_PER_PEER);
    }

    private void acquire(String key, int limit) {
        var rows = jdbc.queryForList("""
            INSERT INTO performance_write_limits(bucket_key,window_start,hits)
            VALUES (?,date_trunc('minute',now()),1)
            ON CONFLICT (bucket_key,window_start) DO UPDATE SET hits=performance_write_limits.hits+1
            WHERE performance_write_limits.hits < ? RETURNING hits
            """, Integer.class, key, limit);
        if (rows.isEmpty()) throw new PerformanceRateLimitException();
    }

    @Scheduled(initialDelay = 600000, fixedDelay = 600000)
    @Transactional
    public void clearExpired() {
        jdbc.update("DELETE FROM performance_write_limits WHERE window_start < now() - interval '1 hour'");
    }
}

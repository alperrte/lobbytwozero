package com.ltz.hardware.infrastructure.persistence.repository;

import com.ltz.hardware.domain.exception.HardwareRateLimitException;
import com.ltz.hardware.domain.entity.HardwareSyncDigest;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class HardwareSyncLimits {
    private final JdbcTemplate jdbc;
    public HardwareSyncLimits(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void preview(UUID userId, String peer) {
        acquire("sync-user:" + userId, 20);
        peer(peer);
    }

    public void apply(UUID userId, UUID deviceId, String peer) {
        acquire("sync-apply-user:" + userId, 30);
        acquire("sync-device:" + userId + ":" + deviceId, 10);
        peer(peer);
    }

    private void peer(String peer) {
        if (peer == null || peer.isBlank() || peer.length() > 128) throw new IllegalArgumentException("Geçersiz bağlantı");
        acquire("sync-peer:" + HardwareSyncDigest.hash(peer), 120);
    }

    private void acquire(String key, int limit) {
        var rows = jdbc.queryForList("""
            INSERT INTO hardware_write_limits(bucket_key,window_start,hits)
            VALUES (?,date_trunc('minute',now()),1)
            ON CONFLICT (bucket_key,window_start) DO UPDATE SET hits=hardware_write_limits.hits+1
            WHERE hardware_write_limits.hits < ? RETURNING hits
            """, Integer.class, key, limit);
        if (rows.isEmpty()) throw new HardwareRateLimitException();
    }

}

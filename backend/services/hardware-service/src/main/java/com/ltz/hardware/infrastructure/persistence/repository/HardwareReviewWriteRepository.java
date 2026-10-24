package com.ltz.hardware.infrastructure.persistence.repository;

import com.ltz.hardware.domain.entity.CatalogText;
import com.ltz.hardware.domain.entity.HardwareReviewText;
import com.ltz.hardware.domain.exception.HardwareRateLimitException;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class HardwareReviewWriteRepository {
    private final JdbcTemplate jdbc;
    public HardwareReviewWriteRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void limit(UUID actorId, String peer, boolean moderator) {
        if (peer == null || peer.isBlank() || peer.length() > 128) throw new IllegalArgumentException("Geçersiz bağlantı bağlamı");
        acquire((moderator ? "moderator:" : "actor:") + actorId, moderator ? 60 : 20);
        acquire("peer:" + HardwareReviewText.hash(peer), 120);
    }

    private void acquire(String key, int limit) {
        var updated = jdbc.queryForList("""
            INSERT INTO hardware_write_limits(bucket_key,window_start,hits)
            VALUES (?,date_trunc('minute',now()),1)
            ON CONFLICT (bucket_key,window_start) DO UPDATE SET hits=hardware_write_limits.hits+1
            WHERE hardware_write_limits.hits < ? RETURNING hits
            """, Integer.class, key, limit);
        if (updated.isEmpty()) throw new HardwareRateLimitException();
    }

    public void append(UUID actorId, String action, UUID targetId, String reason) {
        jdbc.update("INSERT INTO hardware_review_audit(id,actor_id,action,target_id,reason) VALUES (?,?,?,?,?)",
                UUID.randomUUID(), actorId, action, targetId, CatalogText.clean(reason, 500));
    }

    @Scheduled(initialDelay = 60000, fixedDelay = 60000)
    @Transactional
    public void clearExpiredLimits() {
        jdbc.update("DELETE FROM hardware_write_limits WHERE window_start < now() - interval '2 minutes'");
    }
}

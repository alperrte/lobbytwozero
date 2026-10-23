package com.ltz.hardware.infrastructure.persistence.repository;

import com.ltz.hardware.domain.exception.HardwareSyncCapacityException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class HardwareSyncReceiptRepository {
    private final JdbcTemplate jdbc;
    public HardwareSyncReceiptRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record Cached(String requestHash, String responseBody) { }

    public Optional<Cached> findLive(UUID profileId, UUID key, Instant now) {
        return jdbc.query("""
                SELECT request_hash,response_body::text FROM hardware_sync_receipts
                WHERE profile_id=? AND idempotency_key=? AND expires_at>?
                """, (row, _) -> new Cached(row.getString(1), row.getString(2)), profileId, key, Timestamp.from(now))
                .stream().findFirst();
    }

    /** Parent profile lock serializes capacity, unique keys and business changes. */
    public void store(UUID profileId, UUID key, UUID generation, long sequence,
            String hash, String responseBody, Instant now) {
        jdbc.update("DELETE FROM hardware_sync_receipts WHERE profile_id=? AND expires_at<=?", profileId, Timestamp.from(now));
        Long count = jdbc.queryForObject("SELECT count(*) FROM hardware_sync_receipts WHERE profile_id=?", Long.class, profileId);
        if (count != null && count >= 500) throw new HardwareSyncCapacityException();
        jdbc.update("""
            INSERT INTO hardware_sync_receipts(id,profile_id,idempotency_key,generation,sequence,request_hash,response_body,created_at,expires_at)
            VALUES (?,?,?,?,?,?,CAST(? AS jsonb),?,?)
            """, UUID.randomUUID(), profileId, key, generation, sequence, hash, responseBody,
                Timestamp.from(now), Timestamp.from(now.plusSeconds(86400)));
    }

    @Scheduled(initialDelay = 3600000, fixedDelay = 3600000)
    @Transactional
    public void clearExpired() { jdbc.update("DELETE FROM hardware_sync_receipts WHERE expires_at<=now()"); }
}

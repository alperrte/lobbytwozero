package com.ltz.performance.infrastructure.persistence.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Idempotency makbuzları; hesap kilidi altında çağrıldığı için kapasite ve benzersizlik serileşir. */
@Repository
public class SyncBatchRepository {
    public static final int MAX_LIVE_PER_DEVICE = 500;
    public static final long RETENTION_SECONDS = 86400;

    private final JdbcTemplate jdbc;

    public SyncBatchRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record StoredBatch(UUID id, String requestHash, int sampleCount, Instant receivedAt) { }

    public Optional<StoredBatch> findLive(UUID deviceLinkId, UUID idempotencyKey, Instant now) {
        return jdbc.query("""
                SELECT id,request_hash,sample_count,received_at FROM sync_batches
                WHERE device_link_id=? AND idempotency_key=? AND expires_at>?
                """, (row, _) -> new StoredBatch(row.getObject(1, UUID.class), row.getString(2), row.getInt(3),
                        row.getTimestamp(4).toInstant()), deviceLinkId, idempotencyKey, Timestamp.from(now))
                .stream().findFirst();
    }

    /** @return false ise cihazın canlı makbuz kapasitesi dolmuştur. */
    public boolean hasCapacity(UUID deviceLinkId, Instant now) {
        jdbc.update("DELETE FROM sync_batches WHERE device_link_id=? AND expires_at<=?",
                deviceLinkId, Timestamp.from(now));
        Long count = jdbc.queryForObject("SELECT count(*) FROM sync_batches WHERE device_link_id=?",
                Long.class, deviceLinkId);
        return count == null || count < MAX_LIVE_PER_DEVICE;
    }

    /** Kaydedilecek makbuzun istemci tarafından gelen alanları. */
    public record NewBatch(UUID deviceLinkId, UUID userId, UUID idempotencyKey, String requestHash,
            int schemaVersion, int sampleCount, Instant clientSentAt) { }

    public StoredBatch store(NewBatch batch, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO sync_batches(id,device_link_id,user_id,idempotency_key,request_hash,schema_version,
                sample_count,client_sent_at,received_at,expires_at)
            VALUES (?,?,?,?,?,?,?,?,?,?)
            """, id, batch.deviceLinkId(), batch.userId(), batch.idempotencyKey(), batch.requestHash(),
                batch.schemaVersion(), batch.sampleCount(), Timestamp.from(batch.clientSentAt()),
                Timestamp.from(now), Timestamp.from(now.plusSeconds(RETENTION_SECONDS)));
        return new StoredBatch(id, batch.requestHash(), batch.sampleCount(), now);
    }

    @Scheduled(initialDelay = 3600000, fixedDelay = 3600000)
    @Transactional
    public void clearExpired() { jdbc.update("DELETE FROM sync_batches WHERE expires_at<=now()"); }
}

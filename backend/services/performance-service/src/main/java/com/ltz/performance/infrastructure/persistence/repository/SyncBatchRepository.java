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
    /** Cihaz başına canlı (24 saat) makbuz sayısı; 60 gönderim/dakika sınırının ~25 dakikalık tamponudur. */
    public static final int MAX_LIVE_PER_DEVICE = 1500;
    public static final long RETENTION_SECONDS = 86400;

    private final JdbcTemplate jdbc;

    public SyncBatchRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record StoredBatch(UUID id, String requestHash, int sampleCount, int storedCount, int duplicateCount,
            Instant receivedAt) { }

    public Optional<StoredBatch> findLive(UUID deviceLinkId, UUID idempotencyKey, Instant now) {
        return jdbc.query("""
                SELECT id,request_hash,sample_count,stored_count,duplicate_count,received_at FROM sync_batches
                WHERE device_link_id=? AND idempotency_key=? AND expires_at>?
                """, (row, _) -> new StoredBatch(row.getObject(1, UUID.class), row.getString(2), row.getInt(3),
                        row.getInt(4), row.getInt(5), row.getTimestamp(6).toInstant()),
                deviceLinkId, idempotencyKey, Timestamp.from(now)).stream().findFirst();
    }

    /** @return false ise cihazın canlı makbuz kapasitesi dolmuştur. */
    public boolean hasCapacity(UUID deviceLinkId, Instant now) {
        jdbc.update("DELETE FROM sync_batches WHERE device_link_id=? AND expires_at<=?",
                deviceLinkId, Timestamp.from(now));
        Long count = jdbc.queryForObject("SELECT count(*) FROM sync_batches WHERE device_link_id=?",
                Long.class, deviceLinkId);
        return count == null || count < MAX_LIVE_PER_DEVICE;
    }

    /** Makbuzun kimliği; {@code id} örneklerin {@code batch_id} denetim izi için önceden üretilir. */
    public record BatchKey(UUID id, UUID deviceLinkId, UUID userId, UUID idempotencyKey) { }

    /** İstemciden gelen içerik alanları ({@code sampleCount}: v1'de bildirilen, v2'de gönderilen örnek sayısı). */
    public record BatchContent(String requestHash, int schemaVersion, int sampleCount, Instant clientSentAt) { }

    /** v2 sonucu: hedef oturum ve yazılan/yinelenen sayıları. v1 için {@link #NONE}. */
    public record SampleOutcome(UUID sessionId, int stored, int duplicate) {
        public static final SampleOutcome NONE = new SampleOutcome(null, 0, 0);
    }

    public record NewBatch(BatchKey key, BatchContent content, SampleOutcome outcome) { }

    public StoredBatch store(NewBatch batch, Instant now) {
        var key = batch.key();
        var content = batch.content();
        var outcome = batch.outcome();
        jdbc.update("""
            INSERT INTO sync_batches(id,device_link_id,user_id,idempotency_key,request_hash,schema_version,
                sample_count,client_sent_at,received_at,expires_at,session_id,stored_count,duplicate_count)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
            """, key.id(), key.deviceLinkId(), key.userId(), key.idempotencyKey(), content.requestHash(),
                content.schemaVersion(), content.sampleCount(), Timestamp.from(content.clientSentAt()),
                Timestamp.from(now), Timestamp.from(now.plusSeconds(RETENTION_SECONDS)),
                outcome.sessionId(), outcome.stored(), outcome.duplicate());
        return new StoredBatch(key.id(), content.requestHash(), content.sampleCount(), outcome.stored(),
                outcome.duplicate(), now);
    }

    @Scheduled(initialDelay = 3600000, fixedDelay = 3600000)
    @Transactional
    public void clearExpired() { jdbc.update("DELETE FROM sync_batches WHERE expires_at<=now()"); }
}

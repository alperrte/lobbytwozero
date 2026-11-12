package com.ltz.library.infrastructure.persistence.repository;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** {@code library_summary_outbox}: kullanıcı başına tek "özet değişti" işareti. Çağıranın transaction'ı gerekir. */
@Repository
public class LibrarySummaryOutboxRepository {
    /** Teslim için alınan işaret; {@code changeSeq} teslimden sonra yalnız bu sürümü silmek içindir. */
    public record Claim(UUID userId, long changeSeq) { }

    private static final String CLAIM = """
            WITH due AS (SELECT user_id FROM library_summary_outbox WHERE next_attempt_at <= clock_timestamp()
                         ORDER BY next_attempt_at, user_id LIMIT :batch FOR UPDATE SKIP LOCKED)
            UPDATE library_summary_outbox o SET next_attempt_at = clock_timestamp() + CAST(:leaseSeconds AS bigint) * interval '1 second'
            FROM due WHERE o.user_id = due.user_id RETURNING o.user_id, o.change_seq
            """;
    private static final String FAIL = """
            UPDATE library_summary_outbox SET attempts = attempts + 1,
                next_attempt_at = clock_timestamp() + LEAST(CAST(:maxSeconds AS double precision),
                    CAST(:baseSeconds AS double precision) * power(2, LEAST(attempts, 20))) * interval '1 second'
            WHERE user_id = :userId
            """;

    private final EntityManager entities;

    public LibrarySummaryOutboxRepository(EntityManager entities) { this.entities = entities; }

    /** Kullanıcının özeti değişti: satır yoksa açılır, varsa sürümü artar (deneme geri çekilmesi korunur). */
    public void mark(UUID userId) {
        entities.createNativeQuery("""
                INSERT INTO library_summary_outbox (user_id) VALUES (:userId)
                ON CONFLICT (user_id) DO UPDATE SET change_seq = library_summary_outbox.change_seq + 1
                """).setParameter("userId", userId).executeUpdate();
    }

    /** Zamanı gelmiş işaretleri atomik alır ve {@code leaseSeconds} süreyle kiralar (çöken relay sonra yeniden alınır). */
    public List<Claim> claimDue(int batch, long leaseSeconds) {
        List<?> rows = entities.createNativeQuery(CLAIM).setParameter("batch", batch)
                .setParameter("leaseSeconds", leaseSeconds).getResultList();
        return rows.stream().map(row -> (Object[]) row)
                .map(row -> new Claim((UUID) row[0], ((Number) row[1]).longValue())).toList();
    }

    /** Teslim edilen sürümü siler. Arada değişiklik olduysa (sürüm farklı) satır kalır ve hemen yeniden zamanı gelir. */
    public boolean acknowledge(UUID userId, long changeSeq) {
        int deleted = entities.createNativeQuery("DELETE FROM library_summary_outbox WHERE user_id = :userId AND change_seq = :seq")
                .setParameter("userId", userId).setParameter("seq", changeSeq).executeUpdate();
        if (deleted == 0) {
            entities.createNativeQuery("UPDATE library_summary_outbox SET attempts = 0, next_attempt_at = clock_timestamp() WHERE user_id = :userId")
                    .setParameter("userId", userId).executeUpdate();
        }
        return deleted > 0;
    }

    /** Başarısız teslim: deneme artar, {@code base * 2^önceki deneme} kadar (en çok {@code max}) sonra yeniden denenir. */
    public void fail(UUID userId, long baseSeconds, long maxSeconds) {
        entities.createNativeQuery(FAIL).setParameter("userId", userId)
                .setParameter("baseSeconds", baseSeconds).setParameter("maxSeconds", maxSeconds).executeUpdate();
    }

    public void erase(UUID userId) {
        entities.createNativeQuery("DELETE FROM library_summary_outbox WHERE user_id = :userId")
                .setParameter("userId", userId).executeUpdate();
    }
}

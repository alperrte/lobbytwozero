package com.ltz.performance.infrastructure.persistence.repository;

import com.ltz.performance.domain.entity.TelemetrySample;
import com.ltz.performance.domain.enums.SessionStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Ölçüm örnekleri. Çağıranlar hesap kilidi ({@link PerformanceAccountAccessGuard}) altındadır; sayaç ve kota
 * güncellemeleri bu sayede yarışmaz. Örnekler yalnız eklenir veya oturum/örnek silme ile topluca silinir.
 */
@Repository
public class PerformanceSampleRepository {
    /** Oturum başına en çok örnek (örn. 10 metrik x 1/sn x ~2,7 saat); aşılırsa gönderim reddedilir. */
    public static final int MAX_PER_SESSION = 100_000;
    /** Hesap başına en çok örnek; en çok 1000 oturum olsa da toplam depolamayı sınırlar. */
    public static final long MAX_PER_USER = 500_000;

    private final JdbcTemplate jdbc;

    public PerformanceSampleRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Hedef oturumun örnek kabulü için gereken alanları; yoksa veya başkasınınsa boş (tek tip 404). */
    public record SessionState(UUID id, SessionStatus status, Instant startedAt, Instant endedAt, int sampleCount) { }

    public Optional<SessionState> lockOwnedSession(UUID sessionId, UUID userId) {
        return jdbc.query("""
                SELECT id,status,started_at,ended_at,sample_count FROM performance_sessions
                WHERE id=? AND user_id=? FOR UPDATE
                """, (row, _) -> new SessionState(row.getObject(1, UUID.class),
                        SessionStatus.valueOf(row.getString(2)), row.getTimestamp(3).toInstant(),
                        row.getTimestamp(4) == null ? null : row.getTimestamp(4).toInstant(), row.getInt(5)),
                sessionId, userId).stream().findFirst();
    }

    public long countForUser(UUID userId) {
        Long total = jdbc.queryForObject(
                "SELECT COALESCE(SUM(sample_count),0) FROM performance_sessions WHERE user_id=?", Long.class, userId);
        return total == null ? 0 : total;
    }

    /** Kimlik alanları; ölçüm içeriğinden ayrı tutulur. */
    public record Target(UUID sessionId, UUID userId, UUID deviceLinkId, UUID batchId) { }

    /**
     * Örnekleri tek ifadede ekler; aynı (oturum, metrik, an) zaten varsa atlanır (çoğaltılmaz).
     * Zaman milisaniye epoch olarak taşınır ve SQL'de tam çevrilir (kayan nokta yuvarlaması yok).
     *
     * @return gerçekten yazılan satır sayısı; {@code samples.size() - dönüş} = yinelenen
     */
    public int insertIgnoringDuplicates(Target target, List<TelemetrySample> samples, Instant receivedAt) {
        var metrics = samples.stream().map(s -> s.metric().name()).toArray(String[]::new);
        var values = samples.stream().map(TelemetrySample::value).toArray(Double[]::new);
        var millis = samples.stream().map(s -> s.capturedAt().toEpochMilli()).toArray(Long[]::new);
        Integer inserted = jdbc.execute((ConnectionCallback<Integer>) connection -> {
            var sql = """
                    INSERT INTO performance_samples(id,session_id,user_id,device_link_id,batch_id,metric,value,
                        captured_at,received_at,source)
                    SELECT gen_random_uuid(),?,?,?,?,t.m,t.v,
                        timestamptz 'epoch' + t.c * interval '1 millisecond',?,'DEVICE'
                    FROM unnest(?::text[], ?::float8[], ?::int8[]) AS t(m,v,c)
                    ON CONFLICT (session_id, metric, captured_at) DO NOTHING
                    """;
            try (var statement = connection.prepareStatement(sql)) {
                statement.setObject(1, target.sessionId());
                statement.setObject(2, target.userId());
                statement.setObject(3, target.deviceLinkId());
                statement.setObject(4, target.batchId());
                statement.setTimestamp(5, Timestamp.from(receivedAt));
                statement.setArray(6, connection.createArrayOf("text", metrics));
                statement.setArray(7, connection.createArrayOf("float8", values));
                statement.setArray(8, connection.createArrayOf("int8", millis));
                return statement.executeUpdate();
            }
        });
        return Objects.requireNonNull(inserted, "insert count");
    }

    public void addToSessionCount(UUID sessionId, int delta) {
        jdbc.update("UPDATE performance_sessions SET sample_count=sample_count+? WHERE id=?", delta, sessionId);
    }

    /** Oturumun örneklerini siler ve sayacı sıfırlar; oturumun kendisi korunur. @return silinen örnek sayısı */
    public int deleteForSession(UUID sessionId, UUID userId) {
        int deleted = jdbc.update("DELETE FROM performance_samples WHERE session_id=? AND user_id=?", sessionId, userId);
        jdbc.update("UPDATE performance_sessions SET sample_count=0 WHERE id=? AND user_id=?", sessionId, userId);
        return deleted;
    }
}

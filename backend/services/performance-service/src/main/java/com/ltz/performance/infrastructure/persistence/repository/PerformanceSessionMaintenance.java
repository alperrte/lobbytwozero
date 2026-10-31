package com.ltz.performance.infrastructure.persistence.repository;

import com.ltz.performance.domain.entity.PerformanceSession;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Toplu işlemler: 24 saati aşan ACTIVE oturumların terk edilmiş sayılması ve kullanıcının tüm oturumlarının
 * silinmesi. Satır sayısı saklama sınırıyla (hesap başına 1000 oturum) sınırlıdır; raporlar CASCADE ile gider.
 */
@Repository
public class PerformanceSessionMaintenance {
    private static final String ABANDON = """
        UPDATE performance_sessions SET status='ABANDONED', ended_at=started_at + interval '24 hours',
            version=version+1, updated_at=?
        WHERE status='ACTIVE' AND started_at <= ?""";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public PerformanceSessionMaintenance(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Kullanıcının süresi dolmuş ACTIVE oturumunu ABANDONED yapar; JPA yüklemesinden önce çağrılmalıdır. */
    public int abandonStale(UUID userId, Instant now) {
        return jdbc.update(ABANDON + " AND user_id=?", Timestamp.from(now), Timestamp.from(cutoff(now)), userId);
    }

    @Scheduled(initialDelay = 300000, fixedDelay = 600000)
    @Transactional
    public void abandonAllStale() {
        var now = clock.instant();
        jdbc.update(ABANDON, Timestamp.from(now), Timestamp.from(cutoff(now)));
    }

    /** Silinen oturum sayısını döner; boş küme için 0 (idempotent). */
    public int deleteAll(UUID userId) {
        return jdbc.update("DELETE FROM performance_sessions WHERE user_id=?", userId);
    }

    private static Instant cutoff(Instant now) { return now.minus(PerformanceSession.MAX_ACTIVE); }
}

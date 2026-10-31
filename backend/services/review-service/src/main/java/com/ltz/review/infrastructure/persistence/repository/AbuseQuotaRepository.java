package com.ltz.review.infrastructure.persistence.repository;

import com.ltz.review.domain.enums.AbuseAction;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kota sayaçları PostgreSQL'dedir; servis örnekleri aynı satırı atomik UPSERT ile artırır.
 * Tüketim her zaman kendi transaction'ında kesinleşir; çağıran iş transaction'ının sonucundan etkilenmez.
 */
@Repository
public class AbuseQuotaRepository {
    private final JdbcClient jdbc;

    public AbuseQuotaRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    /** Denemeyi sayar ve pencere içindeki deneme sayısını döndürür; sayaç {@code cap} değerinde durur. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 4)
    public int consume(UUID account, AbuseAction action, Instant windowStart, Instant windowEnd, int cap) {
        return jdbc.sql("""
                INSERT INTO review_rate_limits AS quota (account_hash, action, window_start, expires_at, attempts)
                VALUES (:hash, :action, :start, :end, 1)
                ON CONFLICT (account_hash, action, window_start)
                DO UPDATE SET attempts = LEAST(quota.attempts + 1, :cap)
                RETURNING attempts
                """)
                .param("hash", AccountWriteLock.hash(account)).param("action", action.name())
                .param("start", OffsetDateTime.ofInstant(windowStart, ZoneOffset.UTC))
                .param("end", OffsetDateTime.ofInstant(windowEnd, ZoneOffset.UTC))
                .param("cap", cap).query(Integer.class).single();
    }

    /** Pencere bitiminden 5 dakika sonra siler; bu pay, servis örnekleri arasındaki saat farkını karşılar. */
    @Transactional(timeout = 4)
    public int removeExpired() {
        return jdbc.sql("DELETE FROM review_rate_limits WHERE expires_at <= clock_timestamp() - interval '5 minutes'").update();
    }
}

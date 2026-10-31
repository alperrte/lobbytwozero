package com.ltz.performance.infrastructure.persistence.repository;

import com.ltz.performance.application.port.AccountErasure;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PerformanceAccountErasureRepository implements AccountErasure {
    private final JdbcTemplate jdbc;
    private final PerformanceAccountAccessGuard guard;

    public PerformanceAccountErasureRepository(JdbcTemplate jdbc, PerformanceAccountAccessGuard guard) {
        this.jdbc = jdbc;
        this.guard = guard;
    }

    @Override
    public boolean erase(UUID accountId) {
        // Kilit ve ifade beklemeleri Auth'un beş saniyelik HTTP zaman aşımının altında tutulur.
        jdbc.queryForList("SELECT set_config('lock_timeout','2000ms',true)");
        jdbc.queryForList("SELECT set_config('statement_timeout','3000ms',true)");
        guard.lock(accountId);
        jdbc.update("""
            INSERT INTO performance_account_erasure_fences(account_hash,expires_at)
            VALUES (?,clock_timestamp()+interval '17 minutes') ON CONFLICT(account_hash)
            DO UPDATE SET expires_at=GREATEST(performance_account_erasure_fences.expires_at,EXCLUDED.expires_at)
            """, PerformanceAccountAccessGuard.accountHash(accountId));
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT erase_performance_account(?)", Boolean.class, accountId));
    }
}

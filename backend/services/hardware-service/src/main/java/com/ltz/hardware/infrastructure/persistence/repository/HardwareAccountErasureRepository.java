package com.ltz.hardware.infrastructure.persistence.repository;

import com.ltz.hardware.application.port.HardwareAccountErasure;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class HardwareAccountErasureRepository implements HardwareAccountErasure {
    private final JdbcTemplate jdbc;
    private final HardwareAccountAccessGuard guard;
    public HardwareAccountErasureRepository(JdbcTemplate jdbc, HardwareAccountAccessGuard guard) {
        this.jdbc = jdbc;
        this.guard = guard;
    }

    @Override public boolean erase(UUID accountId) {
        // Lock and statement waits are bounded below Auth's five-second HTTP timeout.
        jdbc.queryForList("SELECT set_config('lock_timeout','2000ms',true)");
        jdbc.queryForList("SELECT set_config('statement_timeout','3000ms',true)");
        guard.lock(accountId);
        jdbc.update("""
            INSERT INTO hardware_account_erasure_fences(account_hash,expires_at)
            VALUES (?,clock_timestamp()+interval '17 minutes') ON CONFLICT(account_hash)
            DO UPDATE SET expires_at=GREATEST(hardware_account_erasure_fences.expires_at,EXCLUDED.expires_at)
            """, HardwareAccountAccessGuard.accountHash(accountId));
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT erase_hardware_account(?)", Boolean.class, accountId));
    }
}

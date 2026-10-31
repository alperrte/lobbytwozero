package com.ltz.performance.infrastructure.persistence.repository;

import com.ltz.performance.domain.entity.PerformanceDigest;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Hesap silme çiti ve hesap bazlı danışma kilidi: silinen hesap yeni kayıt üretemez, yazmalar serileşir. */
@Repository
public class PerformanceAccountAccessGuard {
    private final JdbcTemplate jdbc;

    public PerformanceAccountAccessGuard(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public static String accountHash(UUID accountId) { return PerformanceDigest.hash(accountId.toString()); }

    public void lock(UUID accountId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Account lock requires a transaction");
        }
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?, 42))", accountHash(accountId));
    }

    public void requireActive(UUID accountId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && !TransactionSynchronizationManager.isCurrentTransactionReadOnly()) lock(accountId);
        Boolean erased = jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM performance_account_erasure_fences
            WHERE account_hash=? AND expires_at > clock_timestamp())
            """, Boolean.class, accountHash(accountId));
        if (Boolean.TRUE.equals(erased)) throw new CredentialsExpiredException("Account access expired");
    }

    @Scheduled(initialDelay = 60000, fixedDelay = 60000)
    @Transactional
    public void clearExpiredFences() {
        jdbc.update("DELETE FROM performance_account_erasure_fences WHERE expires_at <= clock_timestamp()");
    }
}

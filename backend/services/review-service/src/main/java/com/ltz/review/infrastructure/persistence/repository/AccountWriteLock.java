package com.ltz.review.infrastructure.persistence.repository;

import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import com.ltz.review.domain.exception.ReviewException;
import com.ltz.review.infrastructure.security.UserTokenPolicy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Hesap silme ve aynı hesabın mutasyonları tek DB transaction sırasını paylaşır. */
@Repository
public class AccountWriteLock {
    private final JdbcClient jdbc;

    public AccountWriteLock(JdbcClient jdbc) { this.jdbc = jdbc; }

    private void acquire(UUID userId) {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:account, 0))")
                .param("account", userId.toString()).query((rs, row) -> 0).single();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void requireWritable(UUID userId) {
        acquire(userId);
        Boolean blocked = jdbc.sql("SELECT EXISTS (SELECT 1 FROM review_erasure_guards WHERE account_hash = ? AND expires_at > clock_timestamp())")
                .param(hash(userId)).query(Boolean.class).single();
        if (!Boolean.FALSE.equals(blocked)) throw new ReviewException(403, "Hesap verisi silindikten sonra bu işlem yapılamaz.");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void block(UUID userId) {
        acquire(userId);
        jdbc.sql("""
                INSERT INTO review_erasure_guards (account_hash, expires_at)
                VALUES (:hash, clock_timestamp() + :seconds * interval '1 second')
                ON CONFLICT (account_hash) DO UPDATE
                SET expires_at = greatest(review_erasure_guards.expires_at, EXCLUDED.expires_at)
                """).param("hash", hash(userId)).param("seconds", UserTokenPolicy.ERASURE_BLOCK_TTL.toSeconds()).update();
    }

    static String hash(UUID userId) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(userId.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 kullanılamıyor.", exception);
        }
    }
}

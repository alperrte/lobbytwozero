package com.ltz.auth.infrastructure.persistence.repository;

import java.sql.Timestamp;
import java.time.Instant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * Süresi dolan geçici kayıtların toplu silinmesi. Her ifade sabittir (dinamik SQL yok) ve {@code limit} ile sınırlıdır;
 * böylece uzun kilit/işlem oluşmaz ve çoklu örnekte iki temizlik aynı satırı sildiğinde zararsızdır (idempotent).
 * Dönen değer silinen satır sayısıdır.
 */
@Repository
@RequiredArgsConstructor
public class RetentionCleanupRepository {

    private final JdbcTemplate jdbc;

    public int deleteExpiredOAuthFlows(Instant cutoff, int limit) {
        return jdbc.update("delete from oauth_flows where state_hash in "
                + "(select state_hash from oauth_flows where expires_at < ? limit ?)", Timestamp.from(cutoff), limit);
    }

    public int deleteExpiredOAuthLinkFlows(Instant cutoff, int limit) {
        return jdbc.update("delete from oauth_link_flows where state_hash in "
                + "(select state_hash from oauth_link_flows where expires_at < ? limit ?)", Timestamp.from(cutoff), limit);
    }

    public int deleteOldOpenIdNonces(Instant cutoff, int limit) {
        return jdbc.update("delete from openid_nonces where nonce_hash in "
                + "(select nonce_hash from openid_nonces where seen_at < ? limit ?)", Timestamp.from(cutoff), limit);
    }

    public int deleteExpiredSteamProofs(Instant cutoff, int limit) {
        return jdbc.update("delete from steam_registrations where token_hash in "
                + "(select token_hash from steam_registrations where expires_at < ? limit ?)",
                Timestamp.from(cutoff), limit);
    }

    /** Kilidi bitmiş ve uzun süredir güncellenmeyen sayaçlar. Aktif kilitler asla silinmez. */
    public int deleteStaleThrottles(Instant cutoff, int limit) {
        return jdbc.update("delete from attempt_throttles where key_hash in (select key_hash from attempt_throttles "
                + "where updated_at < ? and (locked_until is null or locked_until < ?) limit ?)",
                Timestamp.from(cutoff), Timestamp.from(cutoff), limit);
    }

    public int deleteExpiredVerificationCodes(Instant cutoff, int limit) {
        return jdbc.update("delete from email_verification_codes where id in "
                + "(select id from email_verification_codes where expires_at < ? limit ?)", Timestamp.from(cutoff), limit);
    }

    public int deleteExpiredPasswordResetTokens(Instant cutoff, int limit) {
        return jdbc.update("delete from password_reset_tokens where id in "
                + "(select id from password_reset_tokens where expires_at < ? limit ?)", Timestamp.from(cutoff), limit);
    }

    public int deleteExpiredDeletionTokens(Instant cutoff, int limit) {
        return jdbc.update("delete from account_deletion_tokens where id in "
                + "(select id from account_deletion_tokens where expires_at < ? limit ?)", Timestamp.from(cutoff), limit);
    }

    public int deleteExpiredMfaChallenges(Instant cutoff, int limit) {
        return jdbc.update("delete from mfa_challenges where id in "
                + "(select id from mfa_challenges where expires_at < ? limit ?)", Timestamp.from(cutoff), limit);
    }

    /** Mutlak ömrü dolmuş oturumlar; refresh token'ları ON DELETE CASCADE ile birlikte silinir. */
    public int deleteExpiredSessions(Instant cutoff, int limit) {
        return jdbc.update("delete from auth_sessions where id in "
                + "(select id from auth_sessions where expires_at < ? limit ?)", Timestamp.from(cutoff), limit);
    }

    /** Yalnız saklama süresi yapılandırıldığında çağrılır (hukuki karar gerektirir). */
    public int deleteOldSecurityEvents(Instant cutoff, int limit) {
        return jdbc.update("delete from security_events where id in "
                + "(select id from security_events where created_at < ? limit ?)", Timestamp.from(cutoff), limit);
    }
}

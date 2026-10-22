package com.ltz.auth.domain.entity;

import java.time.Instant;
import java.util.UUID;

import com.ltz.auth.domain.enums.PasswordResetInvalidationReason;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Tek kullanımlık, süreli parola sıfırlama token'ı; yalnız özeti saklanır. */
@Entity
@Table(name = "password_reset_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Getter(AccessLevel.NONE)
    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "invalidated_at")
    private Instant invalidatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "invalidation_reason", length = 32)
    private PasswordResetInvalidationReason invalidationReason;

    public static PasswordResetToken issue(UUID accountId, String tokenHash, Instant now, Instant expiresAt) {
        PasswordResetToken token = new PasswordResetToken();
        token.accountId = accountId;
        token.tokenHash = tokenHash;
        token.createdAt = now;
        token.expiresAt = expiresAt;
        return token;
    }

    public boolean isActive() {
        return consumedAt == null && invalidatedAt == null;
    }

    public boolean isUsableAt(Instant now) {
        return isActive() && now.isBefore(expiresAt);
    }

    public void consume(Instant now) {
        if (!isActive()) {
            throw new IllegalStateException("Password reset token is no longer active");
        }
        consumedAt = now;
    }

    public void invalidate(PasswordResetInvalidationReason reason, Instant now) {
        if (!isActive()) {
            throw new IllegalStateException("Password reset token is no longer active");
        }
        invalidationReason = reason;
        invalidatedAt = now;
    }
}

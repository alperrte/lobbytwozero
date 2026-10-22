package com.ltz.auth.domain.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Tek kullanımlık 2FA kurtarma kodu; yalnız HMAC özeti saklanır. */
@Entity
@Table(name = "recovery_codes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecoveryCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Getter(AccessLevel.NONE)
    @Column(name = "code_hash", nullable = false, updatable = false, length = 64)
    private String codeHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "used_at")
    private Instant usedAt;

    public static RecoveryCode issue(UUID accountId, String codeHash, Instant now) {
        RecoveryCode code = new RecoveryCode();
        code.accountId = accountId;
        code.codeHash = codeHash;
        code.createdAt = now;
        return code;
    }

    public void markUsed(Instant now) {
        if (usedAt != null) {
            throw new IllegalStateException("Recovery code is already used");
        }
        usedAt = now;
    }
}

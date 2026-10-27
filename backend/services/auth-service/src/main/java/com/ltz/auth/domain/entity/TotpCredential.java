package com.ltz.auth.domain.entity;

import java.time.Instant;
import java.util.UUID;

import com.ltz.auth.domain.enums.TotpStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Hesabın TOTP secret'ı (şifreli). Etkinleştirme doğrulanana kadar PENDING kalır. */
@Entity
@Table(name = "totp_credentials")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TotpCredential {

    @Id
    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "secret_ciphertext", nullable = false)
    private byte[] secretCiphertext;

    @Column(name = "secret_nonce", nullable = false)
    private byte[] secretNonce;

    @Column(name = "key_version", nullable = false)
    private short keyVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TotpStatus status;

    /** Son kabul edilen zaman adımı; aynı veya önceki adımın kodu tekrar kabul edilmez (replay koruması). */
    @Column(name = "last_used_step")
    private Long lastUsedStep;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Version
    private long version;

    public static TotpCredential pending(UUID accountId, byte[] ciphertext, byte[] nonce, short keyVersion,
                                         Instant now) {
        TotpCredential credential = new TotpCredential();
        credential.accountId = accountId;
        credential.replaceSecret(ciphertext, nonce, keyVersion, now);
        return credential;
    }

    public boolean isActive() {
        return status == TotpStatus.ACTIVE;
    }

    public void replaceSecret(byte[] ciphertext, byte[] nonce, short keyVersion, Instant now) {
        if (isActive()) {
            throw new IllegalStateException("Active TOTP secret cannot be replaced");
        }
        secretCiphertext = ciphertext;
        secretNonce = nonce;
        this.keyVersion = keyVersion;
        status = TotpStatus.PENDING;
        lastUsedStep = null;
        createdAt = now;
    }

    public boolean acceptsStep(long step) {
        return lastUsedStep == null || step > lastUsedStep;
    }

    public void markStepUsed(long step) {
        lastUsedStep = step;
    }

    public void activate(long step, Instant now) {
        markStepUsed(step);
        status = TotpStatus.ACTIVE;
        activatedAt = now;
    }
}

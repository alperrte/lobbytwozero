package com.ltz.auth.domain.entity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.ltz.auth.domain.enums.CodeInvalidationReason;
import com.ltz.auth.domain.enums.VerificationPurpose;

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

/** Tek kullanımlık doğrulama kodu kaydı. Kodun kendisi değil, yalnız HMAC özeti tutulur. */
@Entity
@Table(name = "email_verification_codes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerificationCode {

    /** Gereksinim sabiti (AUTH-02): kod 6 hanelidir ve başında 0 olabilir. */
    public static final int CODE_LENGTH = 6;
    public static final String CODE_PATTERN = "^[0-9]{" + CODE_LENGTH + "}$";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private VerificationPurpose purpose;

    @Getter(AccessLevel.NONE)
    @Column(name = "code_hash", nullable = false, updatable = false, length = 64)
    private String codeHash;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    /** Yalnız EMAIL_CHANGE kodunda dolu: doğrulanınca hesaba yazılacak yeni adres. */
    @Column(name = "new_email", updatable = false, length = Account.EMAIL_MAX_LENGTH)
    private String newEmail;

    @Column(name = "new_email_normalized", updatable = false, length = Account.EMAIL_MAX_LENGTH)
    private String newEmailNormalized;

    @Column(name = "invalidated_at")
    private Instant invalidatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "invalidation_reason", length = 32)
    private CodeInvalidationReason invalidationReason;

    public static EmailVerificationCode issue(UUID accountId, VerificationPurpose purpose, String codeHash,
                                              Instant now, Duration timeToLive) {
        EmailVerificationCode code = new EmailVerificationCode();
        code.accountId = accountId;
        code.purpose = purpose;
        code.codeHash = codeHash;
        code.createdAt = now;
        code.expiresAt = now.plus(timeToLive);
        return code;
    }

    /** E-posta değiştirme kodu; kod yeni adrese gider ve doğrulanana kadar hesap adresi değişmez. */
    public static EmailVerificationCode issueForEmailChange(UUID accountId, String newEmail, String codeHash,
                                                            Instant now, Duration timeToLive) {
        EmailVerificationCode code = issue(accountId, VerificationPurpose.EMAIL_CHANGE, codeHash, now, timeToLive);
        code.newEmail = newEmail.strip();
        code.newEmailNormalized = Account.normalizeEmail(newEmail);
        return code;
    }

    public boolean isActive() {
        return consumedAt == null && invalidatedAt == null;
    }

    public boolean acceptsAttempt(Instant now, int maxAttempts) {
        return isActive() && now.isBefore(expiresAt) && attempts < maxAttempts;
    }

    public void recordAttempt() {
        attempts++;
    }

    /** Sabit zamanlı karşılaştırma; zamanlama farkı üzerinden özet tahmini engellenir. */
    public boolean hasHash(String candidateHash) {
        return MessageDigest.isEqual(
                codeHash.getBytes(StandardCharsets.US_ASCII),
                candidateHash.getBytes(StandardCharsets.US_ASCII));
    }

    public void consume(Instant now) {
        if (!isActive()) {
            throw new IllegalStateException("Verification code is no longer active");
        }
        consumedAt = now;
    }

    public void invalidate(CodeInvalidationReason reason, Instant now) {
        if (!isActive()) {
            throw new IllegalStateException("Verification code is no longer active");
        }
        invalidationReason = reason;
        invalidatedAt = now;
    }
}

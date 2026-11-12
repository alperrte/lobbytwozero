package com.ltz.auth.domain.entity;

import java.time.Instant;
import java.util.UUID;

import com.ltz.auth.domain.enums.AgeVerificationMethod;
import com.ltz.auth.domain.enums.AgeVerificationRevoker;

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

/**
 * Hesabın yaş doğrulaması SONUCU. Doğum tarihi, yaş veya kimlik bilgisi tutulmaz; yalnız ne zaman, hangi yöntemle
 * doğrulandığı ve geri alınıp alınmadığı saklanır. Hesap başına tek satır: geri alınan doğrulama yeniden beyanda
 * aynı satırda tekrar etkinleşir.
 */
@Entity
@Table(name = "account_age_verifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountAgeVerification {

    @Id
    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "verified_at", nullable = false)
    private Instant verifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AgeVerificationMethod method;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "revoked_by", length = 8)
    private AgeVerificationRevoker revokedBy;

    @Version
    private long version;

    public static AccountAgeVerification verified(UUID accountId, AgeVerificationMethod method, Instant now) {
        AccountAgeVerification verification = new AccountAgeVerification();
        verification.accountId = accountId;
        verification.verifiedAt = now;
        verification.method = method;
        return verification;
    }

    /** Doğrulama geçerli mi (geri alınmamış). Access token iddiası yalnız buna bakar. */
    public boolean isActive() {
        return revokedAt == null;
    }

    /** Geri alır; zaten geri alınmışsa değişmez (idempotent). @return durum değiştiyse true */
    public boolean revoke(AgeVerificationRevoker by, Instant now) {
        if (!isActive()) {
            return false;
        }
        revokedAt = now;
        revokedBy = by;
        return true;
    }

    /** Geri alınmış doğrulamayı yeniden etkinleştirir (yeni beyan). Etkin doğrulamada çağrılmaz. */
    public void reactivate(AgeVerificationMethod newMethod, Instant now) {
        if (isActive()) {
            throw new IllegalStateException("Age verification is already active");
        }
        verifiedAt = now;
        method = newMethod;
        revokedAt = null;
        revokedBy = null;
    }
}

package com.ltz.performance.domain.entity;

import com.ltz.performance.domain.enums.SyncConsentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;

/** Kullanıcı başına tek ve açık senkronizasyon izni; izin yoksa hiçbir cihaz ölçüm gönderemez. */
@Entity
@Table(name = "sync_consents")
@Getter
public class SyncConsent {
    @Id @Column(name = "user_id", nullable = false, updatable = false) private UUID userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private SyncConsentStatus status;
    @Column(name = "granted_at") private Instant grantedAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    @Version private Long version;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected SyncConsent() { }

    public static SyncConsent granted(UUID userId, Instant now) {
        var consent = new SyncConsent();
        consent.userId = Objects.requireNonNull(userId);
        consent.createdAt = now;
        consent.applyGrant(now);
        return consent;
    }

    /** İdempotent: zaten verilmiş izin değişmez. */
    public void grant(Instant now) {
        if (!isGranted()) applyGrant(now);
    }

    /** İdempotent: zaten iptal edilmiş izin değişmez. */
    public void revoke(Instant now) {
        if (!isGranted()) return;
        status = SyncConsentStatus.REVOKED;
        revokedAt = now;
        updatedAt = now;
    }

    public boolean isGranted() { return status == SyncConsentStatus.GRANTED; }

    private void applyGrant(Instant now) {
        status = SyncConsentStatus.GRANTED;
        grantedAt = now;
        revokedAt = null;
        updatedAt = now;
    }
}

package com.ltz.auth.domain.entity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.ltz.auth.domain.enums.DeviceRevokeReason;
import com.ltz.auth.domain.enums.DeviceStatus;

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

/**
 * Masaüstü cihazın kimlik bilgisi (B yolu). Gizli anahtarın kendisi saklanmaz, yalnız SHA-256 özeti. Cihaz bu kimlikle
 * kısa ömürlü, dar yetkili bir cihaz token'ı ({@code dev+jwt}) alır. İptal geri alınmaz.
 */
@Entity
@Table(name = "device_credentials")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeviceCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(nullable = false, updatable = false, length = 64)
    private String label;

    @Getter(AccessLevel.NONE)
    @Column(name = "secret_hash", nullable = false, updatable = false, length = 64)
    private String secretHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DeviceStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "revoke_reason", length = 32)
    private DeviceRevokeReason revokeReason;

    public static DeviceCredential bind(UUID accountId, String label, String secretHash, Instant now, Duration ttl) {
        DeviceCredential device = new DeviceCredential();
        device.accountId = accountId;
        device.label = label;
        device.secretHash = secretHash;
        device.status = DeviceStatus.ACTIVE;
        device.createdAt = now;
        device.expiresAt = now.plus(ttl);
        return device;
    }

    public boolean isUsableAt(Instant now) {
        return status == DeviceStatus.ACTIVE && now.isBefore(expiresAt);
    }

    /** Sabit zamanlı karşılaştırma: aday özet, saklanan özetle aynı mı. */
    public boolean hasHash(String candidateHash) {
        return candidateHash != null && MessageDigest.isEqual(
                secretHash.getBytes(StandardCharsets.US_ASCII), candidateHash.getBytes(StandardCharsets.US_ASCII));
    }

    /** İptal idempotenttir; ilk neden korunur. @return bu çağrıyla iptal edildi mi */
    public boolean revoke(DeviceRevokeReason reason, Instant now) {
        if (status == DeviceStatus.REVOKED) {
            return false;
        }
        status = DeviceStatus.REVOKED;
        revokedAt = now;
        revokeReason = reason;
        return true;
    }

    /** Son kullanım yalnız {@code minInterval} geçtiyse yazılır (her token isteğinde yazma yükü olmasın). */
    public void markUsed(Instant now, Duration minInterval) {
        if (lastUsedAt == null || now.isAfter(lastUsedAt.plus(minInterval))) {
            lastUsedAt = now;
        }
    }
}

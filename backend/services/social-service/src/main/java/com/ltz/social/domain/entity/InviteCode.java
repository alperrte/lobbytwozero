package com.ltz.social.domain.entity;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.ltz.social.domain.exception.SocialErrorCode;
import com.ltz.social.domain.exception.SocialException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Kullanıcının paylaşabileceği arkadaşlık davet kodu. Kodu giren kişi, kod sahibine normal arkadaşlık isteği
 * gönderir. Kod; iptal edilene, süresi dolana ya da kullanım sınırına ulaşana kadar geçerlidir.
 */
@Entity
@Table(name = "friend_invite_codes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InviteCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "code", nullable = false, updatable = false, length = 10)
    private String code;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "max_uses", nullable = false, updatable = false)
    private int maxUses;

    @Column(name = "used_count", nullable = false)
    private int usedCount;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public static InviteCode issue(UUID userId, String code, Instant now, Duration ttl, int maxUses) {
        InviteCode invite = new InviteCode();
        invite.userId = userId;
        invite.code = code;
        invite.createdAt = now;
        invite.expiresAt = now.plus(ttl);
        invite.maxUses = maxUses;
        return invite;
    }

    /** İptal edilmemiş, süresi dolmamış ve kullanım hakkı kalmış mı. */
    public boolean isUsableAt(Instant now) {
        return revokedAt == null && now.isBefore(expiresAt) && usedCount < maxUses;
    }

    /** Başarılı bir arkadaşlık isteği oluştuğunda sayacı artırır; kullanılamaz kod için 404 ile aynı hata. */
    public void recordUse(Instant now) {
        if (!isUsableAt(now)) {
            throw new SocialException(SocialErrorCode.INVITE_CODE_NOT_FOUND);
        }
        usedCount++;
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }
}

package com.ltz.auth.domain.entity;

import java.time.Instant;

import com.ltz.auth.domain.enums.SteamProofPurpose;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Doğrulanmış SteamID'ye bağlı kısa ömürlü, tek kullanımlık kanıt (token yalnız özet olarak saklanır).
 * Amaç {@link SteamProofPurpose} ile ayrılır; bir amacın token'ı diğerinde kullanılamaz.
 */
@Entity
@Table(name = "steam_registrations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SteamRegistration {

    @Id
    @Column(name = "token_hash", length = 64)
    private String tokenHash;

    @Column(name = "steam_id", nullable = false, updatable = false, length = 17)
    private String steamId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 24)
    private SteamProofPurpose purpose;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    public static SteamRegistration issue(String tokenHash, String steamId, SteamProofPurpose purpose, Instant now,
                                          Instant expiresAt) {
        SteamRegistration registration = new SteamRegistration();
        registration.tokenHash = tokenHash;
        registration.steamId = steamId;
        registration.purpose = purpose;
        registration.createdAt = now;
        registration.expiresAt = expiresAt;
        return registration;
    }

    public boolean isUsableAt(Instant now, SteamProofPurpose expectedPurpose) {
        return purpose == expectedPurpose && consumedAt == null && now.isBefore(expiresAt);
    }

    public void consume(Instant now) {
        consumedAt = now;
    }
}

package com.ltz.auth.domain.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Doğrulanmış SteamID için kısa ömürlü, tek kullanımlık kayıt hakkı (token yalnız özet olarak saklanır). */
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

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    public static SteamRegistration issue(String tokenHash, String steamId, Instant now, Instant expiresAt) {
        SteamRegistration registration = new SteamRegistration();
        registration.tokenHash = tokenHash;
        registration.steamId = steamId;
        registration.createdAt = now;
        registration.expiresAt = expiresAt;
        return registration;
    }

    public boolean isUsableAt(Instant now) {
        return consumedAt == null && now.isBefore(expiresAt);
    }

    public void consume(Instant now) {
        consumedAt = now;
    }
}

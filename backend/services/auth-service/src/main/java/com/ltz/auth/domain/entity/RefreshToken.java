package com.ltz.auth.domain.entity;

import java.time.Duration;
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

/** Tek kullanımlık refresh token. Her yenilemede döndürülür (rotation); yalnız özeti saklanır. */
@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Getter(AccessLevel.NONE)
    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "rotated_at")
    private Instant rotatedAt;

    public static RefreshToken issue(UUID sessionId, String tokenHash, Instant now, Instant expiresAt) {
        RefreshToken token = new RefreshToken();
        token.sessionId = sessionId;
        token.tokenHash = tokenHash;
        token.createdAt = now;
        token.expiresAt = expiresAt;
        return token;
    }

    public boolean isRotated() {
        return rotatedAt != null;
    }

    /** Döndürülmüş token kısa süre içinde yeniden geldiyse eşzamanlı yenileme sayılır, saldırı değil. */
    public boolean wasRotatedWithin(Duration grace, Instant now) {
        return isRotated() && now.isBefore(rotatedAt.plus(grace));
    }

    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public void rotate(Instant now) {
        if (isRotated()) {
            throw new IllegalStateException("Refresh token is already rotated");
        }
        rotatedAt = now;
    }
}

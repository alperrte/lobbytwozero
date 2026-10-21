package com.ltz.auth.domain.entity;

import java.time.Instant;
import java.util.UUID;

import com.ltz.auth.domain.enums.AuthMethod;

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

/** Parola doğrulandı, ikinci faktör bekleniyor. Token vermez; yalnız 2FA adımını tamamlamaya yarar. */
@Entity
@Table(name = "mfa_challenges")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MfaChallenge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Getter(AccessLevel.NONE)
    @Column(name = "challenge_hash", nullable = false, updatable = false, length = 64)
    private String challengeHash;

    /** Challenge'ı doğuran ilk faktör (parola veya harici sağlayıcı); oturumun amr değerine yansır. */
    @Enumerated(EnumType.STRING)
    @Column(name = "first_factor", nullable = false, updatable = false, length = 16)
    private AuthMethod firstFactor;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    public static MfaChallenge issue(UUID accountId, String challengeHash, AuthMethod firstFactor, Instant now,
                                     Instant expiresAt) {
        MfaChallenge challenge = new MfaChallenge();
        challenge.accountId = accountId;
        challenge.firstFactor = firstFactor;
        challenge.challengeHash = challengeHash;
        challenge.createdAt = now;
        challenge.expiresAt = expiresAt;
        return challenge;
    }

    public boolean acceptsAttempt(Instant now, int maxAttempts) {
        return consumedAt == null && now.isBefore(expiresAt) && attempts < maxAttempts;
    }

    public void recordAttempt() {
        attempts++;
    }

    public void consume(Instant now) {
        consumedAt = now;
    }
}

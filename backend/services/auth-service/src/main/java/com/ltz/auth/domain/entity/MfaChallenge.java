package com.ltz.auth.domain.entity;

import java.time.Instant;
import java.util.UUID;

import com.ltz.auth.domain.enums.AuthMethod;
import com.ltz.auth.domain.enums.MfaChallengePurpose;

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

    /** LOGIN: TOTP ile giriş ikinci adımı; ENROLLMENT: zorunlu 2FA'nın ilk kurulumu (kurulum bitince oturum açılır). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 12)
    private MfaChallengePurpose purpose = MfaChallengePurpose.LOGIN;

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

    /** Zorunlu 2FA'nın ilk kurulumu için challenge; TOTP kodu içermez, yalnız kurulum adımlarını açar. */
    public static MfaChallenge issueEnrollment(UUID accountId, String challengeHash, AuthMethod firstFactor, Instant now,
                                               Instant expiresAt) {
        MfaChallenge challenge = issue(accountId, challengeHash, firstFactor, now, expiresAt);
        challenge.purpose = MfaChallengePurpose.ENROLLMENT;
        return challenge;
    }

    public boolean isEnrollment() {
        return purpose == MfaChallengePurpose.ENROLLMENT;
    }

    /** Kullanılmamış ve süresi dolmamış (deneme sayısına bakmaz). */
    public boolean isOpen(Instant now) {
        return consumedAt == null && now.isBefore(expiresAt);
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

package com.ltz.auth.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.ltz.auth.domain.entity.Account;
import com.ltz.auth.domain.entity.MfaChallenge;
import com.ltz.auth.domain.entity.RecoveryCode;
import com.ltz.auth.domain.entity.TotpCredential;
import com.ltz.auth.domain.enums.AuthMethod;
import com.ltz.auth.domain.enums.RoleCode;
import com.ltz.auth.domain.enums.SecurityEventType;
import com.ltz.auth.domain.enums.TotpStatus;
import com.ltz.auth.domain.exception.AuthErrorCode;
import com.ltz.auth.domain.exception.AuthException;
import com.ltz.auth.infrastructure.config.TwoFactorProperties;
import com.ltz.auth.infrastructure.persistence.repository.AccountRepository;
import com.ltz.auth.infrastructure.persistence.repository.MfaChallengeRepository;
import com.ltz.auth.infrastructure.persistence.repository.RecoveryCodeRepository;
import com.ltz.auth.infrastructure.persistence.repository.TotpCredentialRepository;
import com.ltz.auth.infrastructure.security.SecretCipher;
import com.ltz.auth.infrastructure.security.SecretCipher.EncryptedSecret;
import com.ltz.auth.infrastructure.security.SecureTokens;
import com.ltz.auth.infrastructure.security.TotpCodes;
import com.ltz.auth.infrastructure.security.VerificationCodeHasher;

import lombok.RequiredArgsConstructor;

/**
 * AUTH-04: TOTP kurulumu, giriş challenge'ı, kurtarma kodları ve kapatma. İkinci faktör başarıyla
 * doğrulanmadan oturum açılmaz. E-posta OTP bir 2FA yöntemi veya yedek yolu değildir.
 */
@Service
@RequiredArgsConstructor
public class TwoFactorService {

    static final String TOTP_THROTTLE_DOMAIN = "TOTP_ACCOUNT";
    static final String RECOVERY_CODE_DOMAIN = "RECOVERY_CODE";

    private final TotpCredentialRepository credentialRepository;
    private final RecoveryCodeRepository recoveryCodeRepository;
    private final MfaChallengeRepository challengeRepository;
    private final AccountRepository accountRepository;
    private final TotpCodes totpCodes;
    private final SecretCipher secretCipher;
    private final SecureTokens secureTokens;
    private final VerificationCodeHasher hasher;
    private final AttemptThrottleService attemptThrottle;
    private final SessionService sessionService;
    private final SecurityEventRecorder securityEventRecorder;
    private final PasswordEncoder passwordEncoder;
    private final TwoFactorProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public boolean isEnabled(UUID accountId) {
        return credentialRepository.existsByAccountIdAndStatus(accountId, TotpStatus.ACTIVE);
    }

    public boolean isRequiredFor(Set<RoleCode> roles) {
        return !Collections.disjoint(roles, properties.requiredRoles());
    }

    @Transactional
    public LoginResult issueChallenge(UUID accountId, AuthMethod firstFactor) {
        Instant now = clock.instant();
        String challengeId = secureTokens.newOpaqueToken();
        Instant expiresAt = now.plus(properties.challengeTtl());
        challengeRepository.save(MfaChallenge.issue(accountId, secureTokens.sha256Hex(challengeId), firstFactor, now,
                expiresAt));
        securityEventRecorder.record(SecurityEventType.MFA_CHALLENGE_ISSUED, accountId);
        return LoginResult.mfaRequired(challengeId, expiresAt);
    }

    /** Challenge + TOTP veya kurtarma kodu ile girişi tamamlar. Denemeler hem challenge hem hesap bazında sınırlıdır. */
    public IssuedSession completeLogin(String challengeId, String code, String recoveryCode) {
        String challengeHash = secureTokens.sha256Hex(challengeId);
        UUID accountId = challengeRepository.findByChallengeHash(challengeHash)
                .map(MfaChallenge::getAccountId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.MFA_FAILED));
        String throttleKey = throttleKey(accountId);
        attemptThrottle.reserve(throttleKey);
        IssuedSession session = transactionTemplate.execute(status -> verifyChallenge(challengeHash, code, recoveryCode));
        if (session == null) {
            securityEventRecorder.record(SecurityEventType.MFA_FAILED, accountId);
            throw new AuthException(AuthErrorCode.MFA_FAILED);
        }
        attemptThrottle.clear(throttleKey);
        securityEventRecorder.record(SecurityEventType.MFA_SUCCEEDED, accountId);
        return session;
    }

    /** Yeni (PENDING) secret üretir; etkin TOTP varken çağrılamaz. Secret yalnız bu yanıtta görünür. */
    @Transactional
    public TotpSetup startSetup(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.SESSION_INVALID));
        Instant now = clock.instant();
        Optional<TotpCredential> existing = credentialRepository.findByAccountIdForUpdate(accountId);
        if (existing.filter(TotpCredential::isActive).isPresent()) {
            throw new AuthException(AuthErrorCode.TWO_FACTOR_ALREADY_ENABLED);
        }
        byte[] secret = totpCodes.newSecret();
        EncryptedSecret encrypted = secretCipher.encrypt(secret, accountId);
        existing.ifPresentOrElse(
                credential -> credential.replaceSecret(encrypted.ciphertext(), encrypted.nonce(), encrypted.keyVersion(), now),
                () -> credentialRepository.save(TotpCredential.pending(accountId, encrypted.ciphertext(),
                        encrypted.nonce(), encrypted.keyVersion(), now)));
        securityEventRecorder.record(SecurityEventType.TWO_FACTOR_SETUP_STARTED, accountId);
        return new TotpSetup(totpCodes.base32(secret),
                totpCodes.otpauthUri(properties.issuer(), account.getUsername(), secret));
    }

    /** Uygulamadaki ilk kodla doğrulanınca TOTP etkinleşir ve kurtarma kodları bir kez döner. */
    public List<String> activate(UUID accountId, String code) {
        String throttleKey = throttleKey(accountId);
        attemptThrottle.reserve(throttleKey);
        List<String> recoveryCodes = transactionTemplate.execute(status -> {
            Instant now = clock.instant();
            TotpCredential credential = credentialRepository.findByAccountIdForUpdate(accountId)
                    .filter(candidate -> !candidate.isActive())
                    .orElseThrow(() -> new AuthException(AuthErrorCode.TWO_FACTOR_NOT_PENDING));
            OptionalLong step = matchingUnusedStep(credential, code, now);
            if (step.isEmpty()) {
                return null;
            }
            credential.activate(step.getAsLong(), now);
            securityEventRecorder.record(SecurityEventType.TWO_FACTOR_ENABLED, accountId);
            return replaceRecoveryCodes(accountId, now);
        });
        if (recoveryCodes == null) {
            securityEventRecorder.record(SecurityEventType.MFA_FAILED, accountId);
            throw new AuthException(AuthErrorCode.INVALID_TWO_FACTOR_CODE);
        }
        attemptThrottle.clear(throttleKey);
        return recoveryCodes;
    }

    /** Kapatma yeniden kimlik doğrulama ister: parola + TOTP veya kurtarma kodu. Zorunlu rollerde kapatılamaz. */
    public void disable(UUID accountId, String password, String code, String recoveryCode) {
        String throttleKey = throttleKey(accountId);
        attemptThrottle.reserve(throttleKey);
        Account account = transactionTemplate.execute(status -> accountRepository.findWithRolesById(accountId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.SESSION_INVALID)));
        if (isRequiredFor(account.roleCodes())) {
            throw new AuthException(AuthErrorCode.TWO_FACTOR_REQUIRED_FOR_ROLE);
        }
        boolean passwordMatches = account.passwordHash()
                .map(hash -> passwordEncoder.matches(password, hash))
                .orElse(false);
        if (!passwordMatches) {
            securityEventRecorder.record(SecurityEventType.MFA_FAILED, accountId);
            throw new AuthException(AuthErrorCode.INVALID_CREDENTIALS);
        }
        Boolean disabled = transactionTemplate.execute(status -> {
            Instant now = clock.instant();
            TotpCredential credential = credentialRepository.findByAccountIdForUpdate(accountId)
                    .filter(TotpCredential::isActive)
                    .orElseThrow(() -> new AuthException(AuthErrorCode.TWO_FACTOR_NOT_ENABLED));
            if (verifySecondFactor(accountId, code, recoveryCode, now).isEmpty()) {
                return false;
            }
            credentialRepository.delete(credential);
            recoveryCodeRepository.deleteAllByAccountId(accountId);
            securityEventRecorder.record(SecurityEventType.TWO_FACTOR_DISABLED, accountId);
            return true;
        });
        if (!Boolean.TRUE.equals(disabled)) {
            securityEventRecorder.record(SecurityEventType.MFA_FAILED, accountId);
            throw new AuthException(AuthErrorCode.INVALID_TWO_FACTOR_CODE);
        }
        attemptThrottle.clear(throttleKey);
    }

    private IssuedSession verifyChallenge(String challengeHash, String code, String recoveryCode) {
        Instant now = clock.instant();
        MfaChallenge challenge = challengeRepository.findByChallengeHashForUpdate(challengeHash)
                .filter(candidate -> candidate.acceptsAttempt(now, properties.maxChallengeAttempts()))
                .orElse(null);
        if (challenge == null) {
            return null;
        }
        // Deneme, sonuçtan bağımsız olarak sayılır ve işlemle birlikte kalıcı olur.
        challenge.recordAttempt();
        Optional<AuthMethod> method = verifySecondFactor(challenge.getAccountId(), code, recoveryCode, now);
        if (method.isEmpty()) {
            return null;
        }
        challenge.consume(now);
        return sessionService.start(challenge.getAccountId(), List.of(challenge.getFirstFactor(), method.get()));
    }

    private Optional<AuthMethod> verifySecondFactor(UUID accountId, String code, String recoveryCode, Instant now) {
        if (code != null) {
            return useTotpCode(accountId, code, now) ? Optional.of(AuthMethod.OTP) : Optional.empty();
        }
        return useRecoveryCode(accountId, recoveryCode, now) ? Optional.of(AuthMethod.RECOVERY_CODE) : Optional.empty();
    }

    private boolean useTotpCode(UUID accountId, String code, Instant now) {
        Optional<TotpCredential> credential = credentialRepository.findByAccountIdForUpdate(accountId)
                .filter(TotpCredential::isActive);
        if (credential.isEmpty()) {
            return false;
        }
        OptionalLong step = matchingUnusedStep(credential.get(), code, now);
        step.ifPresent(credential.get()::markStepUsed);
        return step.isPresent();
    }

    private boolean useRecoveryCode(UUID accountId, String recoveryCode, Instant now) {
        Optional<RecoveryCode> unused = recoveryCodeRepository.findUnusedForUpdate(accountId,
                recoveryCodeHash(accountId, recoveryCode));
        unused.ifPresent(code -> {
            code.markUsed(now);
            securityEventRecorder.record(SecurityEventType.RECOVERY_CODE_USED, accountId);
        });
        return unused.isPresent();
    }

    /** Saat penceresinde eşleşen ve daha önce kullanılmamış (replay değil) adım. */
    private OptionalLong matchingUnusedStep(TotpCredential credential, String code, Instant now) {
        byte[] secret = secretCipher.decrypt(new EncryptedSecret(credential.getSecretCiphertext(),
                credential.getSecretNonce(), credential.getKeyVersion()), credential.getAccountId());
        OptionalLong step = totpCodes.matchingStep(secret, code, now, properties.allowedClockSkewSteps());
        return step.isPresent() && credential.acceptsStep(step.getAsLong()) ? step : OptionalLong.empty();
    }

    private List<String> replaceRecoveryCodes(UUID accountId, Instant now) {
        recoveryCodeRepository.deleteAllByAccountId(accountId);
        List<String> codes = new ArrayList<>();
        for (int index = 0; index < properties.recoveryCodeCount(); index++) {
            String code = secureTokens.newRecoveryCode();
            recoveryCodeRepository.save(RecoveryCode.issue(accountId, recoveryCodeHash(accountId, code), now));
            codes.add(code);
        }
        return codes;
    }

    private String recoveryCodeHash(UUID accountId, String code) {
        return hasher.hash(RECOVERY_CODE_DOMAIN, accountId + ":" + secureTokens.normalizeRecoveryCode(code));
    }

    private String throttleKey(UUID accountId) {
        return hasher.hash(TOTP_THROTTLE_DOMAIN, accountId.toString());
    }
}

package com.ltz.auth.application.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.ltz.auth.domain.entity.Account;
import com.ltz.auth.domain.enums.AccountStatus;
import com.ltz.auth.domain.enums.AuthMethod;
import com.ltz.auth.domain.enums.RoleCode;
import com.ltz.auth.domain.enums.SecurityEventType;
import com.ltz.auth.domain.exception.AuthErrorCode;
import com.ltz.auth.domain.exception.AuthException;
import com.ltz.auth.infrastructure.persistence.repository.AccountRepository;
import com.ltz.auth.infrastructure.security.VerificationCodeHasher;

/**
 * AUTH-03: e-posta veya kullanıcı adı + parola ile giriş.
 * <ul>
 *   <li>Yanlış kimlik ile yanlış parola aynı 401'i alır; bilinmeyen hesapta da parola hash'i doğrulanır
 *       (sahte hash), böylece yanıt süresi hesap varlığını belli etmez.</li>
 *   <li>Hesap durumu yalnız parola doğruysa bildirilir; parolayı bilen kişi hesabın sahibidir.</li>
 *   <li>TOTP etkinse token verilmez, yalnız MFA challenge döner. Zorunlu rollerde TOTP yoksa oturum verilmez.</li>
 * </ul>
 */
@Service
public class LoginService {

    static final String LOGIN_THROTTLE_DOMAIN = "LOGIN_IDENTIFIER";
    private static final char EMAIL_MARKER = '@';

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AttemptThrottleService attemptThrottle;
    private final SessionService sessionService;
    private final TwoFactorService twoFactorService;
    private final SecurityEventRecorder securityEventRecorder;
    private final VerificationCodeHasher hasher;
    private final TransactionTemplate transactionTemplate;
    /** Bilinmeyen hesapta da aynı maliyetli doğrulamayı yapmak için açılışta üretilen rastgele hash. */
    private final String dummyPasswordHash;

    public LoginService(AccountRepository accountRepository, PasswordEncoder passwordEncoder,
                        AttemptThrottleService attemptThrottle, SessionService sessionService,
                        TwoFactorService twoFactorService, SecurityEventRecorder securityEventRecorder,
                        VerificationCodeHasher hasher, TransactionTemplate transactionTemplate) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.attemptThrottle = attemptThrottle;
        this.sessionService = sessionService;
        this.twoFactorService = twoFactorService;
        this.securityEventRecorder = securityEventRecorder;
        this.hasher = hasher;
        this.transactionTemplate = transactionTemplate;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public LoginResult login(String identifier, String password) {
        String normalizedIdentifier = normalize(identifier);
        String throttleKey = hasher.hash(LOGIN_THROTTLE_DOMAIN, normalizedIdentifier);
        attemptThrottle.reserve(throttleKey);
        Candidate candidate = transactionTemplate.execute(status -> findCandidate(normalizedIdentifier));
        String passwordHash = candidate == null ? null : candidate.passwordHash();
        boolean matches = passwordEncoder.matches(password, passwordHash == null ? dummyPasswordHash : passwordHash)
                && passwordHash != null;
        if (!matches) {
            securityEventRecorder.record(SecurityEventType.LOGIN_FAILED, candidate == null ? null : candidate.accountId());
            throw new AuthException(AuthErrorCode.INVALID_CREDENTIALS);
        }
        attemptThrottle.clear(throttleKey);
        return complete(candidate, AuthMethod.PWD);
    }

    /**
     * Harici sağlayıcının kimliğini doğruladığı hesap için oturum akışı (Steam/Google). Hesap durumu, 2FA ve zorunlu
     * rol kuralları parola girişiyle birebir aynıdır; harici giriş 2FA'yı atlatmanın yolu değildir.
     */
    public LoginResult loginVerifiedIdentity(UUID accountId, AuthMethod firstFactor) {
        Candidate candidate = transactionTemplate.execute(status -> accountRepository.findWithRolesById(accountId)
                .map(LoginService::toCandidate)
                .orElse(null));
        if (candidate == null) {
            throw new AuthException(AuthErrorCode.ACCOUNT_UNAVAILABLE);
        }
        return complete(candidate, firstFactor);
    }

    private LoginResult complete(Candidate candidate, AuthMethod firstFactor) {
        if (candidate.status() != AccountStatus.ACTIVE) {
            securityEventRecorder.record(SecurityEventType.LOGIN_REJECTED_ACCOUNT_STATUS, candidate.accountId());
            throw new AuthException(statusError(candidate.status()));
        }
        if (twoFactorService.isEnabled(candidate.accountId())) {
            return twoFactorService.issueChallenge(candidate.accountId(), firstFactor);
        }
        if (twoFactorService.isRequiredFor(candidate.roles())) {
            securityEventRecorder.record(SecurityEventType.LOGIN_REJECTED_TWO_FACTOR_REQUIRED, candidate.accountId());
            throw new AuthException(AuthErrorCode.TWO_FACTOR_ENROLLMENT_REQUIRED);
        }
        return LoginResult.authenticated(sessionService.start(candidate.accountId(), List.of(firstFactor)));
    }

    private Candidate findCandidate(String normalizedIdentifier) {
        return (normalizedIdentifier.indexOf(EMAIL_MARKER) >= 0
                ? accountRepository.findWithRolesByEmailNormalized(normalizedIdentifier)
                : accountRepository.findWithRolesByUsernameNormalized(normalizedIdentifier))
                .map(LoginService::toCandidate)
                .orElse(null);
    }

    private static Candidate toCandidate(Account account) {
        return new Candidate(account.getId(), account.getStatus(), account.passwordHash().orElse(null),
                account.roleCodes());
    }

    private static String normalize(String identifier) {
        return identifier.indexOf(EMAIL_MARKER) >= 0
                ? Account.normalizeEmail(identifier)
                : Account.normalizeUsername(identifier);
    }

    private static AuthErrorCode statusError(AccountStatus status) {
        return switch (status) {
            case PENDING_VERIFICATION -> AuthErrorCode.EMAIL_NOT_VERIFIED;
            case SUSPENDED -> AuthErrorCode.ACCOUNT_SUSPENDED;
            case BANNED -> AuthErrorCode.ACCOUNT_BANNED;
            case ACTIVE, DISABLED, DELETION_REQUESTED, DELETED -> AuthErrorCode.ACCOUNT_UNAVAILABLE;
        };
    }

    /** İşlem dışına taşınan, lazy yükleme gerektirmeyen hesap görüntüsü. Parola hash'i loglanmaz. */
    private record Candidate(UUID accountId, AccountStatus status, String passwordHash, Set<RoleCode> roles) {

        @Override
        public String toString() {
            return "Candidate[accountId=" + accountId + ", status=" + status + ", passwordHash=***, roles=" + roles + "]";
        }
    }
}

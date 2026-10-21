package com.ltz.auth.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.ltz.auth.application.port.PasswordResetMail;
import com.ltz.auth.domain.entity.Account;
import com.ltz.auth.domain.entity.PasswordResetToken;
import com.ltz.auth.domain.enums.PasswordResetInvalidationReason;
import com.ltz.auth.domain.enums.SecurityEventType;
import com.ltz.auth.domain.enums.SessionRevokeReason;
import com.ltz.auth.domain.exception.AuthErrorCode;
import com.ltz.auth.domain.exception.AuthException;
import com.ltz.auth.infrastructure.config.PasswordResetProperties;
import com.ltz.auth.infrastructure.persistence.repository.AccountRepository;
import com.ltz.auth.infrastructure.persistence.repository.AuthSessionRepository;
import com.ltz.auth.infrastructure.persistence.repository.PasswordResetTokenRepository;
import com.ltz.auth.infrastructure.security.SecureTokens;
import com.ltz.auth.infrastructure.security.VerificationCodeHasher;

import lombok.RequiredArgsConstructor;

/**
 * AUTH-09 parola değiştirme ve AUTH-10 parola sıfırlama. İkisi de başarıyla biterse hesabın TÜM oturumları iptal
 * edilir ve token sürümü artar (eski access token'lar düşer). Hiçbiri 2FA'yı kapatmaz veya otomatik oturum açmaz.
 */
@Service
@RequiredArgsConstructor
public class PasswordService {

    static final String CHANGE_THROTTLE_DOMAIN = "PASSWORD_CHANGE";
    static final String RESET_REQUEST_THROTTLE_DOMAIN = "PASSWORD_RESET_REQUEST";
    private static final String TOKEN_FRAGMENT = "#token=";

    private final AccountRepository accountRepository;
    private final AuthSessionRepository sessionRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AttemptThrottleService attemptThrottle;
    private final VerificationCodeHasher hasher;
    private final SecureTokens secureTokens;
    private final PasswordResetDispatcher dispatcher;
    private final SecurityEventRecorder securityEventRecorder;
    private final PasswordResetProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /** Mevcut parola doğrulanır; denemeler hesap bazında sınırlıdır (çalıntı oturumla parola tahmini engellenir). */
    public void changePassword(UUID accountId, String currentPassword, String newPassword) {
        String throttleKey = hasher.hash(CHANGE_THROTTLE_DOMAIN, accountId.toString());
        attemptThrottle.reserve(throttleKey);
        String currentHash = transactionTemplate.execute(status -> accountRepository.findById(accountId)
                .flatMap(Account::passwordHash).orElse(null));
        if (currentHash == null || !passwordEncoder.matches(currentPassword, currentHash)) {
            securityEventRecorder.record(SecurityEventType.PASSWORD_CHANGE_FAILED, accountId);
            throw new AuthException(AuthErrorCode.CURRENT_PASSWORD_INVALID);
        }
        if (passwordEncoder.matches(newPassword, currentHash)) {
            throw new AuthException(AuthErrorCode.NEW_PASSWORD_SAME_AS_CURRENT);
        }
        String newHash = passwordEncoder.encode(newPassword);
        transactionTemplate.executeWithoutResult(status -> {
            Instant now = clock.instant();
            Account account = accountRepository.findByIdForUpdate(accountId)
                    .orElseThrow(() -> new AuthException(AuthErrorCode.SESSION_INVALID));
            // Eşzamanlı başka bir değişiklik olduysa doğrulanan parola artık geçerli değildir.
            if (!currentHash.equals(account.passwordHash().orElse(null))) {
                throw new AuthException(AuthErrorCode.CURRENT_PASSWORD_INVALID);
            }
            account.changePassword(newHash, now);
            // Toplu güncellemeler persistence context'i temizler; hesap değişikliği önce flush edilir.
            sessionRepository.revokeAllActive(accountId, SessionRevokeReason.PASSWORD_CHANGED, now);
            resetTokenRepository.invalidateActive(accountId, PasswordResetInvalidationReason.PASSWORD_CHANGED, now);
            securityEventRecorder.record(SecurityEventType.PASSWORD_CHANGED, accountId);
        });
        attemptThrottle.clear(throttleKey);
    }

    /**
     * Hesap varlığını sızdırmaz: her durumda sessizce döner. Aynı e-posta için istek sayısı, hesabın var olup
     * olmadığından bağımsız sınırlıdır (429). Yalnız ACTIVE ve parolalı hesaba token üretilir; bekleme süresindeki
     * ikinci istek e-posta üretmez.
     */
    public void requestReset(String email) {
        String normalized = Account.normalizeEmail(email);
        attemptThrottle.reserve(hasher.hash(RESET_REQUEST_THROTTLE_DOMAIN, normalized));
        PasswordResetMail mail = transactionTemplate.execute(status -> issueToken(normalized));
        if (mail != null) {
            dispatcher.dispatch(mail);
        }
    }

    public void resetPassword(String rawToken, String newPassword) {
        String tokenHash = secureTokens.sha256Hex(rawToken);
        // Çöp token için maliyetli parola hash'i hesaplanmaz.
        boolean plausible = Boolean.TRUE.equals(transactionTemplate.execute(status ->
                resetTokenRepository.findByTokenHash(tokenHash).filter(t -> t.isUsableAt(clock.instant())).isPresent()));
        if (!plausible) {
            throw invalidToken();
        }
        String newHash = passwordEncoder.encode(newPassword);
        boolean done = Boolean.TRUE.equals(transactionTemplate.execute(status -> applyReset(tokenHash, newHash)));
        if (!done) {
            throw invalidToken();
        }
    }

    private boolean applyReset(String tokenHash, String newHash) {
        Instant now = clock.instant();
        PasswordResetToken token = resetTokenRepository.findByTokenHashForUpdate(tokenHash)
                .filter(candidate -> candidate.isUsableAt(now))
                .orElse(null);
        if (token == null) {
            return false;
        }
        Account account = accountRepository.findByIdForUpdate(token.getAccountId())
                .filter(Account::isActive)
                .filter(candidate -> candidate.passwordHash().isPresent())
                .orElse(null);
        if (account == null) {
            return false;
        }
        token.consume(now);
        account.changePassword(newHash, now);
        sessionRepository.revokeAllActive(account.getId(), SessionRevokeReason.PASSWORD_RESET, now);
        securityEventRecorder.record(SecurityEventType.PASSWORD_RESET_COMPLETED, account.getId());
        return true;
    }

    private PasswordResetMail issueToken(String normalizedEmail) {
        Optional<Account> eligible = accountRepository.findByEmailNormalizedForUpdate(normalizedEmail)
                .filter(Account::isActive)
                .filter(account -> account.passwordHash().isPresent());
        // Var olmayan hesapta da aynı token üretim/özet işi yapılır (yanıt süresi farkını azaltır).
        String rawToken = secureTokens.newOpaqueToken();
        String tokenHash = secureTokens.sha256Hex(rawToken);
        if (eligible.isEmpty()) {
            return null;
        }
        Account account = eligible.get();
        Instant now = clock.instant();
        boolean coolingDown = resetTokenRepository.findFirstByAccountIdOrderByCreatedAtDesc(account.getId())
                .map(latest -> now.isBefore(latest.getCreatedAt().plus(properties.requestCooldown())))
                .orElse(false);
        if (coolingDown) {
            return null;
        }
        resetTokenRepository.invalidateActive(account.getId(), PasswordResetInvalidationReason.SUPERSEDED, now);
        PasswordResetToken saved = resetTokenRepository.save(PasswordResetToken.issue(account.getId(), tokenHash, now,
                now.plus(properties.tokenTtl())));
        securityEventRecorder.record(SecurityEventType.PASSWORD_RESET_REQUESTED, account.getId());
        return new PasswordResetMail(saved.getId(), account.getId(), account.getEmail(),
                properties.linkBaseUrl() + TOKEN_FRAGMENT + rawToken, properties.tokenTtl());
    }

    private AuthException invalidToken() {
        securityEventRecorder.record(SecurityEventType.PASSWORD_RESET_FAILED, null);
        return new AuthException(AuthErrorCode.PASSWORD_RESET_TOKEN_INVALID);
    }
}

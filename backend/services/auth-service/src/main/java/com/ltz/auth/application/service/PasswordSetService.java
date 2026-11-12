package com.ltz.auth.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.ltz.auth.application.port.SecurityNotificationType;
import com.ltz.auth.application.port.VerificationCodeMail;
import com.ltz.auth.domain.entity.Account;
import com.ltz.auth.domain.entity.EmailVerificationCode;
import com.ltz.auth.domain.enums.CodeInvalidationReason;
import com.ltz.auth.domain.enums.DeviceRevokeReason;
import com.ltz.auth.domain.enums.PasswordResetInvalidationReason;
import com.ltz.auth.domain.enums.SecurityEventType;
import com.ltz.auth.domain.enums.SessionRevokeReason;
import com.ltz.auth.domain.enums.VerificationPurpose;
import com.ltz.auth.domain.exception.AuthErrorCode;
import com.ltz.auth.domain.exception.AuthException;
import com.ltz.auth.infrastructure.config.VerificationProperties;
import com.ltz.auth.infrastructure.persistence.repository.AccountRepository;
import com.ltz.auth.infrastructure.persistence.repository.AuthSessionRepository;
import com.ltz.auth.infrastructure.persistence.repository.EmailVerificationCodeRepository;
import com.ltz.auth.infrastructure.persistence.repository.PasswordResetTokenRepository;
import com.ltz.auth.infrastructure.security.VerificationCodeGenerator;
import com.ltz.auth.infrastructure.security.VerificationCodeHasher;

import lombok.RequiredArgsConstructor;

/**
 * Parolasız (yalnız Google/Steam) hesabın ilk parolasını belirlemesi (B11). İki adım:
 * <ol>
 *   <li><b>İstek:</b> oturumlu kullanıcı; TOTP etkinse ikinci faktör (tek kullanımlık, replay korumalı) ve hesabın
 *       <b>doğrulanmış</b> adresine tek kullanımlık 6 haneli kod.</li>
 *   <li><b>Onay:</b> kod + yeni parola (parola politikası geçerli). Başarıda hesabın tüm oturumları ve cihaz
 *       kimlikleri iptal edilir (giriş yöntemi değişti; çalınmış oturum yeni parolayla kalıcılaşamaz), güvenlik
 *       bildirimi gider ve kullanıcı yeni parolayla yeniden giriş yapar.</li>
 * </ol>
 * Parolası olan hesap bu yolu kullanamaz ({@code PASSWORD_ALREADY_SET}); parola değiştirme ayrı yoldur. "Unuttum" akışı
 * parolasız hesaba token vermeye devam etmez.
 */
@Service
@RequiredArgsConstructor
public class PasswordSetService {

    static final String THROTTLE_DOMAIN = "PASSWORD_SET";
    private static final VerificationPurpose PURPOSE = VerificationPurpose.PASSWORD_SET;
    private static final Duration DAILY_LIMIT_WINDOW = Duration.ofDays(1);

    private final AccountRepository accountRepository;
    private final EmailVerificationCodeRepository codeRepository;
    private final AuthSessionRepository sessionRepository;
    private final DeviceRevoker deviceRevoker;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final TwoFactorService twoFactorService;
    private final AttemptThrottleService attemptThrottle;
    private final VerificationCodeGenerator codeGenerator;
    private final VerificationCodeHasher codeHasher;
    private final VerificationCodeDispatcher dispatcher;
    private final SecurityEventRecorder securityEventRecorder;
    private final SecurityNotificationDispatcher notifier;
    private final VerificationProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public void requestCode(UUID accountId, String code, String recoveryCode) {
        String throttleKey = codeHasher.hash(THROTTLE_DOMAIN, accountId.toString());
        attemptThrottle.reserve(throttleKey);
        Account account = activeAccount(accountId);
        if (account.passwordHash().isPresent()) {
            throw new AuthException(AuthErrorCode.PASSWORD_ALREADY_SET);
        }
        if (twoFactorService.isEnabled(accountId)) {
            if (!twoFactorService.verifySecondFactorForReauthentication(accountId, code, recoveryCode)) {
                securityEventRecorder.recordEvent(SecurityEventType.PASSWORD_SET_REJECTED, accountId);
                throw new AuthException(AuthErrorCode.INVALID_TWO_FACTOR_CODE);
            }
        }
        attemptThrottle.clear(throttleKey);
        transactionTemplate.execute(status -> issueCode(accountId)).ifPresent(dispatcher::dispatch);
    }

    /** Kod hatalıysa, süresi dolduysa veya deneme hakkı bittiyse tek hata döner. */
    public void setPassword(UUID accountId, String submittedCode, String newPassword) {
        Account owner = activeAccount(accountId);
        if (owner.passwordHash().isPresent()) {
            throw new AuthException(AuthErrorCode.PASSWORD_ALREADY_SET);
        }
        passwordPolicy.require(newPassword, owner.getEmail(), owner.getUsername());
        String newHash = passwordEncoder.encode(newPassword);
        Boolean done = transactionTemplate.execute(status -> tryConfirm(accountId, submittedCode, newHash));
        if (!Boolean.TRUE.equals(done)) {
            throw new AuthException(AuthErrorCode.PASSWORD_SET_INVALID);
        }
    }

    private Account activeAccount(UUID accountId) {
        Optional<Account> active = transactionTemplate.execute(status -> accountRepository.findById(accountId)
                .filter(Account::isActive));
        return active.orElseThrow(() -> new AuthException(AuthErrorCode.SESSION_INVALID));
    }

    private Optional<VerificationCodeMail> issueCode(UUID accountId) {
        Account account = accountRepository.findByIdForUpdate(accountId).filter(Account::isActive)
                .orElseThrow(() -> new AuthException(AuthErrorCode.SESSION_INVALID));
        if (account.passwordHash().isPresent()) {
            throw new AuthException(AuthErrorCode.PASSWORD_ALREADY_SET);
        }
        if (!account.hasEmail()) {
            throw new AuthException(AuthErrorCode.ACCOUNT_EMAIL_REQUIRED); // kod e-postayla gider
        }
        Instant now = clock.instant();
        if (isThrottled(accountId, now)) {
            return Optional.empty();
        }
        codeRepository.findActiveForUpdate(accountId, PURPOSE)
                .ifPresent(previous -> previous.invalidate(CodeInvalidationReason.SUPERSEDED, now));
        // Hibernate insert'leri update'lerden önce yazar; tek aktif kod indeksi için önce geçersiz kılma yazılır.
        codeRepository.flush();
        String code = codeGenerator.generate();
        EmailVerificationCode saved = codeRepository.save(EmailVerificationCode.issue(accountId, PURPOSE,
                codeHasher.hash(accountId, PURPOSE, code), now, properties.codeTtl()));
        securityEventRecorder.recordEvent(SecurityEventType.PASSWORD_SET_REQUESTED, accountId);
        return Optional.of(new VerificationCodeMail(saved.getId(), accountId, account.getEmail(), code,
                properties.codeTtl()));
    }

    private boolean isThrottled(UUID accountId, Instant now) {
        boolean coolingDown = codeRepository.findFirstByAccountIdAndPurposeOrderByCreatedAtDesc(accountId, PURPOSE)
                .map(latest -> now.isBefore(latest.getCreatedAt().plus(properties.resendCooldown())))
                .orElse(false);
        return coolingDown || codeRepository.countByAccountIdAndPurposeAndCreatedAtAfter(
                accountId, PURPOSE, now.minus(DAILY_LIMIT_WINDOW)) >= properties.dailyLimit();
    }

    private boolean tryConfirm(UUID accountId, String submittedCode, String newHash) {
        Instant now = clock.instant();
        Optional<EmailVerificationCode> usable = codeRepository.findActiveForUpdate(accountId, PURPOSE)
                .filter(code -> code.acceptsAttempt(now, properties.maxAttempts()));
        if (usable.isEmpty()) {
            securityEventRecorder.recordEvent(SecurityEventType.PASSWORD_SET_FAILED, accountId);
            return false;
        }
        EmailVerificationCode code = usable.get();
        // Deneme, sonuçtan bağımsız olarak sayılır ve işlemle birlikte kalıcı olur.
        code.recordAttempt();
        if (!code.hasAnyHash(codeHasher.allHashes(accountId, PURPOSE, submittedCode))) {
            securityEventRecorder.recordEvent(SecurityEventType.PASSWORD_SET_FAILED, accountId);
            return false;
        }
        Account account = accountRepository.findByIdForUpdate(accountId).filter(Account::isActive)
                .filter(candidate -> candidate.passwordHash().isEmpty()).orElse(null);
        code.consume(now);
        if (account == null) {
            // Kod doğru ama hesap bu arada parola aldı ya da etkin değil: kod yakılır, ayrım yapılmaz.
            securityEventRecorder.recordEvent(SecurityEventType.PASSWORD_SET_FAILED, accountId);
            return false;
        }
        account.changePassword(newHash, now);
        accountRepository.flush();
        // Toplu güncellemeler persistence context'i temizler; hesap değişikliği yukarıda flush edildi.
        sessionRepository.revokeAllActive(accountId, SessionRevokeReason.PASSWORD_CHANGED, now);
        deviceRevoker.revokeAll(accountId, DeviceRevokeReason.PASSWORD_CHANGED, now);
        resetTokenRepository.invalidateActive(accountId, PasswordResetInvalidationReason.PASSWORD_CHANGED, now);
        securityEventRecorder.recordEvent(SecurityEventType.PASSWORD_SET, accountId);
        notifier.notifyAfterCommit(accountId, account.getEmail(), SecurityNotificationType.PASSWORD_CHANGED);
        return true;
    }
}

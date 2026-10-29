package com.ltz.auth.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.ltz.auth.application.port.SecurityNotificationType;
import com.ltz.auth.application.port.VerificationCodeMail;
import com.ltz.auth.domain.entity.Account;
import com.ltz.auth.domain.entity.EmailVerificationCode;
import com.ltz.auth.domain.enums.AuthEventType;
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
 * E-posta adresi değiştirme. İki adım:
 * <ol>
 *   <li><b>İstek:</b> yeniden kimlik doğrulama (parola; TOTP etkinse ikinci faktör), ardından <b>yeni</b> adrese tek
 *       kullanımlık kod. Hesap adresi bu aşamada değişmez.</li>
 *   <li><b>Onay:</b> kod doğrulanınca adres değişir, hesabın tüm oturumları iptal edilir ve token sürümü artar
 *       (e-posta kurtarma kanalı olduğundan eski oturumlar korunmaz); eski adrese gitmiş bekleyen parola sıfırlama
 *       bağlantıları geçersiz kılınır.</li>
 * </ol>
 * Yeni adres başka bir hesapta varsa yanıt farksızdır (e-posta numaralandırması sızmaz): kod gönderilmez, yalnız
 * güvenlik olayı yazılır. Parolasız (yalnız Google/Steam) hesap bu yoldan adres değiştiremez.
 */
@Service
@RequiredArgsConstructor
public class EmailChangeService {

    static final String THROTTLE_DOMAIN = "EMAIL_CHANGE";
    private static final VerificationPurpose PURPOSE = VerificationPurpose.EMAIL_CHANGE;
    private static final Duration DAILY_LIMIT_WINDOW = Duration.ofDays(1);

    private final AccountRepository accountRepository;
    private final EmailVerificationCodeRepository codeRepository;
    private final AuthSessionRepository sessionRepository;
    private final DeviceRevoker deviceRevoker;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final TwoFactorService twoFactorService;
    private final AttemptThrottleService attemptThrottle;
    private final VerificationCodeGenerator codeGenerator;
    private final VerificationCodeHasher codeHasher;
    private final VerificationCodeDispatcher dispatcher;
    private final SecurityEventRecorder securityEventRecorder;
    private final AuthEventPublisher eventPublisher;
    private final SecurityNotificationDispatcher notifier;
    private final VerificationProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public void requestChange(UUID accountId, String newEmail, String password, String code, String recoveryCode) {
        String throttleKey = codeHasher.hash(THROTTLE_DOMAIN, accountId.toString());
        attemptThrottle.reserve(throttleKey);
        Account account = transactionTemplate.execute(status -> accountRepository.findById(accountId)
                .filter(Account::isActive).orElse(null));
        if (account == null) {
            throw new AuthException(AuthErrorCode.SESSION_INVALID);
        }
        String passwordHash = account.passwordHash().orElseThrow(() -> new AuthException(AuthErrorCode.PASSWORD_REQUIRED));
        if (password == null || !passwordEncoder.matches(password, passwordHash)) {
            securityEventRecorder.recordEvent(SecurityEventType.EMAIL_CHANGE_REJECTED, accountId);
            throw new AuthException(AuthErrorCode.CURRENT_PASSWORD_INVALID);
        }
        if (twoFactorService.isEnabled(accountId)
                && !twoFactorService.verifySecondFactorForReauthentication(accountId, code, recoveryCode)) {
            securityEventRecorder.recordEvent(SecurityEventType.EMAIL_CHANGE_REJECTED, accountId);
            throw new AuthException(AuthErrorCode.INVALID_TWO_FACTOR_CODE);
        }
        attemptThrottle.clear(throttleKey);
        transactionTemplate.execute(status -> issueCode(accountId, newEmail)).ifPresent(dispatcher::dispatch);
    }

    /** Kod hatalıysa, süresi dolduysa, deneme hakkı bittiyse veya adres artık alınmışsa tek hata döner. */
    public void confirmChange(UUID accountId, String submittedCode) {
        Boolean changed = transactionTemplate.execute(status -> tryConfirm(accountId, submittedCode));
        if (!Boolean.TRUE.equals(changed)) {
            throw new AuthException(AuthErrorCode.EMAIL_CHANGE_INVALID);
        }
    }

    private Optional<VerificationCodeMail> issueCode(UUID accountId, String newEmail) {
        Account account = accountRepository.findByIdForUpdate(accountId).filter(Account::isActive)
                .orElseThrow(() -> new AuthException(AuthErrorCode.SESSION_INVALID));
        String normalized = Account.normalizeEmail(newEmail);
        if (normalized.equals(account.getEmailNormalized()) || accountRepository.existsByEmailNormalized(normalized)) {
            securityEventRecorder.recordEvent(SecurityEventType.EMAIL_CHANGE_CONFLICT, accountId);
            return Optional.empty();
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
        EmailVerificationCode saved = codeRepository.save(EmailVerificationCode.issueForEmailChange(accountId,
                newEmail, codeHasher.hash(accountId, PURPOSE, code), now, properties.codeTtl()));
        securityEventRecorder.recordEvent(SecurityEventType.EMAIL_CHANGE_REQUESTED, accountId);
        return Optional.of(new VerificationCodeMail(saved.getId(), accountId, newEmail.strip(), code,
                properties.codeTtl()));
    }

    private boolean isThrottled(UUID accountId, Instant now) {
        boolean coolingDown = codeRepository.findFirstByAccountIdAndPurposeOrderByCreatedAtDesc(accountId, PURPOSE)
                .map(latest -> now.isBefore(latest.getCreatedAt().plus(properties.resendCooldown())))
                .orElse(false);
        return coolingDown || codeRepository.countByAccountIdAndPurposeAndCreatedAtAfter(
                accountId, PURPOSE, now.minus(DAILY_LIMIT_WINDOW)) >= properties.dailyLimit();
    }

    private boolean tryConfirm(UUID accountId, String submittedCode) {
        Instant now = clock.instant();
        Optional<EmailVerificationCode> usable = codeRepository.findActiveForUpdate(accountId, PURPOSE)
                .filter(code -> code.acceptsAttempt(now, properties.maxAttempts()));
        if (usable.isEmpty()) {
            securityEventRecorder.recordEvent(SecurityEventType.EMAIL_CHANGE_FAILED, accountId);
            return false;
        }
        EmailVerificationCode code = usable.get();
        // Deneme, sonuçtan bağımsız olarak sayılır ve işlemle birlikte kalıcı olur.
        code.recordAttempt();
        if (!code.hasHash(codeHasher.hash(accountId, PURPOSE, submittedCode))) {
            securityEventRecorder.recordEvent(SecurityEventType.EMAIL_CHANGE_FAILED, accountId);
            return false;
        }
        Account account = accountRepository.findByIdForUpdate(accountId).filter(Account::isActive).orElse(null);
        if (account == null || accountRepository.existsByEmailNormalized(code.getNewEmailNormalized())) {
            // Kod doğru ama adres bu arada başkasına geçti: kod yakılır, ayrım yapılmaz.
            code.consume(now);
            securityEventRecorder.recordEvent(SecurityEventType.EMAIL_CHANGE_CONFLICT, accountId);
            return false;
        }
        code.consume(now);
        String previousEmail = account.getEmail();
        account.changeEmail(code.getNewEmail(), now);
        try {
            accountRepository.flush();
        } catch (DataIntegrityViolationException _) {
            // Yarış: benzersizlik kısıtı son sözü söyler; işlem geri alınır, kullanıcı yeniden dener.
            throw new AuthException(AuthErrorCode.EMAIL_CHANGE_INVALID);
        }
        // Toplu güncellemeler persistence context'i temizler; hesap değişikliği yukarıda flush edildi.
        sessionRepository.revokeAllActive(accountId, SessionRevokeReason.EMAIL_CHANGED, now);
        deviceRevoker.revokeAll(accountId, DeviceRevokeReason.EMAIL_CHANGED, now);
        resetTokenRepository.invalidateActive(accountId, PasswordResetInvalidationReason.PASSWORD_CHANGED, now);
        securityEventRecorder.recordEvent(SecurityEventType.EMAIL_CHANGED, accountId);
        eventPublisher.publish(AuthEventType.ACCOUNT_EMAIL_CHANGED, accountId, null);
        // Bildirim ESKİ adrese gider: hesabı ele geçirilen gerçek sahip izinsiz değişikliği buradan öğrenir.
        notifier.notifyAfterCommit(accountId, previousEmail, SecurityNotificationType.EMAIL_CHANGED);
        return true;
    }
}

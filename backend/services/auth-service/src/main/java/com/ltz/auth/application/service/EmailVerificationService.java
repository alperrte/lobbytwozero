package com.ltz.auth.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.ltz.auth.application.port.VerificationCodeMail;
import com.ltz.auth.domain.entity.Account;
import com.ltz.auth.domain.entity.EmailVerificationCode;
import com.ltz.auth.domain.enums.CodeInvalidationReason;
import com.ltz.auth.domain.enums.SecurityEventType;
import com.ltz.auth.domain.enums.VerificationPurpose;
import com.ltz.auth.domain.exception.EmailVerificationFailedException;
import com.ltz.auth.infrastructure.config.VerificationProperties;
import com.ltz.auth.infrastructure.persistence.repository.AccountRepository;
import com.ltz.auth.infrastructure.persistence.repository.EmailVerificationCodeRepository;
import com.ltz.auth.infrastructure.security.VerificationCodeGenerator;
import com.ltz.auth.infrastructure.security.VerificationCodeHasher;

import lombok.RequiredArgsConstructor;

/** AUTH-02: kod üretimi, yeniden gönderme ve atomik tek kullanımlık doğrulama. */
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    /** "Günlük" sınırın penceresi gereksinim tanımıdır; adet {@link VerificationProperties#dailyLimit()}. */
    static final Duration DAILY_LIMIT_WINDOW = Duration.ofDays(1);
    private static final VerificationPurpose PURPOSE = VerificationPurpose.EMAIL_VERIFICATION;

    private final AccountRepository accountRepository;
    private final EmailVerificationCodeRepository codeRepository;
    private final VerificationCodeGenerator codeGenerator;
    private final VerificationCodeHasher codeHasher;
    private final VerificationCodeDispatcher dispatcher;
    private final SecurityEventRecorder securityEventRecorder;
    private final VerificationProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /**
     * Çağıranın işlemi içinde yeni kod kaydı oluşturur. Açık kod yalnız dönen nesnede, bellekte yaşar;
     * teslim için işlem commit edildikten sonra {@link #dispatch(VerificationCodeMail)} çağrılmalıdır.
     */
    VerificationCodeMail issueCode(Account account, Instant now) {
        String code = codeGenerator.generate();
        EmailVerificationCode saved = codeRepository.save(EmailVerificationCode.issue(
                account.getId(), PURPOSE, codeHasher.hash(account.getId(), PURPOSE, code),
                now, properties.codeTtl()));
        securityEventRecorder.record(SecurityEventType.VERIFICATION_CODE_ISSUED, account.getId());
        return new VerificationCodeMail(saved.getId(), account.getId(), account.getEmail(), code,
                properties.codeTtl());
    }

    void dispatch(VerificationCodeMail mail) {
        dispatcher.dispatch(mail);
    }

    /** Hesap yoksa, doğrulanmışsa veya sınır aşılmışsa da sessizce döner; yanıt hesap varlığını sızdırmaz. */
    public void resend(String email) {
        VerificationCodeMail mail = transactionTemplate.execute(status -> issueForResend(email));
        if (mail != null) {
            dispatch(mail);
        }
    }

    public void confirm(String email, String submittedCode) {
        Boolean verified = transactionTemplate.execute(status -> tryConfirm(email, submittedCode));
        if (!Boolean.TRUE.equals(verified)) {
            throw new EmailVerificationFailedException();
        }
    }

    private VerificationCodeMail issueForResend(String email) {
        Optional<Account> pending = accountRepository
                .findByEmailNormalizedForUpdate(Account.normalizeEmail(email))
                .filter(Account::isPendingVerification);
        if (pending.isEmpty()) {
            return null;
        }
        Account account = pending.get();
        Instant now = clock.instant();
        if (isThrottled(account, now)) {
            securityEventRecorder.record(SecurityEventType.VERIFICATION_CODE_RESEND_THROTTLED, account.getId());
            return null;
        }
        codeRepository.findActiveForUpdate(account.getId(), PURPOSE)
                .ifPresent(code -> code.invalidate(CodeInvalidationReason.SUPERSEDED, now));
        // Hibernate insert'leri update'lerden önce yazar; tek aktif kod indeksi için önce geçersiz kılma yazılır.
        codeRepository.flush();
        return issueCode(account, now);
    }

    private boolean isThrottled(Account account, Instant now) {
        boolean coolingDown = codeRepository
                .findFirstByAccountIdAndPurposeOrderByCreatedAtDesc(account.getId(), PURPOSE)
                .map(latest -> now.isBefore(latest.getCreatedAt().plus(properties.resendCooldown())))
                .orElse(false);
        return coolingDown || codeRepository.countByAccountIdAndPurposeAndCreatedAtAfter(
                account.getId(), PURPOSE, now.minus(DAILY_LIMIT_WINDOW)) >= properties.dailyLimit();
    }

    private boolean tryConfirm(String email, String submittedCode) {
        Optional<Account> pending = accountRepository.findByEmailNormalized(Account.normalizeEmail(email))
                .filter(Account::isPendingVerification);
        if (pending.isEmpty()) {
            securityEventRecorder.record(SecurityEventType.EMAIL_VERIFICATION_FAILED, null);
            return false;
        }
        Account account = pending.get();
        Instant now = clock.instant();
        Optional<EmailVerificationCode> usable = codeRepository.findActiveForUpdate(account.getId(), PURPOSE)
                .filter(code -> code.acceptsAttempt(now, properties.maxAttempts()));
        if (usable.isEmpty()) {
            securityEventRecorder.record(SecurityEventType.EMAIL_VERIFICATION_FAILED, account.getId());
            return false;
        }
        EmailVerificationCode code = usable.get();
        // Deneme, sonuçtan bağımsız olarak sayılır ve işlemle birlikte kalıcı olur.
        code.recordAttempt();
        if (!code.hasHash(codeHasher.hash(account.getId(), PURPOSE, submittedCode))) {
            securityEventRecorder.record(SecurityEventType.EMAIL_VERIFICATION_FAILED, account.getId());
            return false;
        }
        code.consume(now);
        account.markEmailVerified(now);
        securityEventRecorder.record(SecurityEventType.EMAIL_VERIFIED, account.getId());
        return true;
    }
}

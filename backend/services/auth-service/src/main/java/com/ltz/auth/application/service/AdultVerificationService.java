package com.ltz.auth.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.ltz.auth.domain.entity.Account;
import com.ltz.auth.domain.entity.AccountAgeVerification;
import com.ltz.auth.domain.enums.AgeVerificationMethod;
import com.ltz.auth.domain.enums.AgeVerificationRevoker;
import com.ltz.auth.domain.enums.SecurityEventType;
import com.ltz.auth.domain.exception.AuthErrorCode;
import com.ltz.auth.domain.exception.AuthException;
import com.ltz.auth.infrastructure.persistence.repository.AccountAgeVerificationRepository;
import com.ltz.auth.infrastructure.persistence.repository.AccountRepository;
import com.ltz.auth.infrastructure.security.VerificationCodeHasher;

import lombok.RequiredArgsConstructor;

/**
 * Yaş doğrulaması (18+). Kullanıcı doğum tarihini BEYAN eder; sunucu 18+ olup olmadığını hesaplar ve tarihi atar:
 * yalnız sonuç saklanır, tarih ne veritabanına ne loga ne olaya yazılır. Sonuç, access token'daki
 * {@code adult_verified} iddiasının tek kaynağıdır ({@link #isVerified}).
 * <ul>
 *   <li>Yalnız {@code trusted} hesap (2FA veya Google/Steam bağlı) doğrulayabilir; aksi {@code STEP_UP_REQUIRED}.</li>
 *   <li>Hesap başına 24 saatte en çok {@value #MAX_ATTEMPTS} deneme: farklı tarihler denenerek kapı aşılamaz.
 *       Başarılı doğrulama sayacı sıfırlar. Zaten doğrulanmış hesap deneme hakkı harcamaz (idempotent).</li>
 *   <li>Gelecek ya da makul olmayacak kadar eski tarih deneme hakkı harcamaz (bir bilgi vermez, yazım hatasıdır).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AdultVerificationService {

    static final String THROTTLE_DOMAIN = "AGE_VERIFICATION";
    static final int ADULT_AGE_YEARS = 18;
    static final int MAX_AGE_YEARS = 120;
    static final int MAX_ATTEMPTS = 3;
    static final Duration ATTEMPT_WINDOW = Duration.ofHours(24);

    private final AccountAgeVerificationRepository repository;
    private final AccountRepository accountRepository;
    private final AccountTierService tierService;
    private final AttemptThrottleService attemptThrottle;
    private final VerificationCodeHasher hasher;
    private final SecurityEventRecorder securityEventRecorder;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /** Geri alınmamış doğrulama var mı. Token üretiminde her yenilemede çağrılır: tek PK sorgusu, ağ çağrısı yok. */
    public boolean isVerified(UUID accountId) {
        return repository.existsByAccountIdAndRevokedAtIsNull(accountId);
    }

    /** Yaşı beyan eder. Başarıda sonuç kaydedilir; 18 altıysa {@code AGE_NOT_ELIGIBLE} (hiçbir şey yazılmaz). */
    public void verify(UUID accountId, LocalDate birthDate) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        if (birthDate.isAfter(today) || birthDate.isBefore(today.minusYears(MAX_AGE_YEARS))) {
            throw new AuthException(AuthErrorCode.INVALID_BIRTH_DATE);
        }
        Account account = accountRepository.findById(accountId).filter(Account::isActive)
                .orElseThrow(() -> new AuthException(AuthErrorCode.SESSION_INVALID));
        if (!tierService.tierOf(account).isTrusted()) {
            securityEventRecorder.recordEvent(SecurityEventType.AGE_VERIFICATION_REJECTED, accountId);
            throw new AuthException(AuthErrorCode.STEP_UP_REQUIRED);
        }
        if (isVerified(accountId)) {
            return;
        }
        String throttleKey = hasher.hash(THROTTLE_DOMAIN, accountId.toString());
        attemptThrottle.reserve(throttleKey, MAX_ATTEMPTS, ATTEMPT_WINDOW, ATTEMPT_WINDOW);
        if (!isAdultOn(birthDate, today)) {
            securityEventRecorder.recordEvent(SecurityEventType.AGE_VERIFICATION_REJECTED, accountId);
            throw new AuthException(AuthErrorCode.AGE_NOT_ELIGIBLE);
        }
        transactionTemplate.executeWithoutResult(status -> record(accountId, clock.instant()));
        attemptThrottle.clear(throttleKey);
    }

    /** Kullanıcı kendi doğrulamasını geri alır. Doğrulama yoksa ya da zaten geri alınmışsa sessizce döner (idempotent). */
    public void revoke(UUID accountId) {
        transactionTemplate.executeWithoutResult(status -> repository.findByAccountIdForUpdate(accountId)
                .filter(verification -> verification.revoke(AgeVerificationRevoker.SELF, clock.instant()))
                .ifPresent(verification -> securityEventRecorder.recordEvent(
                        SecurityEventType.AGE_VERIFICATION_REVOKED, accountId)));
    }

    private void record(UUID accountId, Instant now) {
        AccountAgeVerification existing = repository.findByAccountIdForUpdate(accountId).orElse(null);
        if (existing == null) {
            repository.save(AccountAgeVerification.verified(accountId, AgeVerificationMethod.SELF_DECLARED, now));
        } else if (existing.isActive()) {
            return; // eşzamanlı başka bir istek zaten doğruladı: yeni beyan eskisini ezmez
        } else {
            existing.reactivate(AgeVerificationMethod.SELF_DECLARED, now);
        }
        securityEventRecorder.recordEvent(SecurityEventType.AGE_VERIFIED, accountId);
    }

    /**
     * Tam 18. yaş günü 18 sayılır. 29 Şubat doğumlular 18. yaşa 1 Mart'ta girer (ihtiyatlı: erken kabul etmez).
     */
    static boolean isAdultOn(LocalDate birthDate, LocalDate today) {
        LocalDate eighteenth = birthDate.plusYears(ADULT_AGE_YEARS);
        if (birthDate.getMonthValue() == 2 && birthDate.getDayOfMonth() == 29) {
            eighteenth = eighteenth.plusDays(1);
        }
        return !eighteenth.isAfter(today);
    }
}

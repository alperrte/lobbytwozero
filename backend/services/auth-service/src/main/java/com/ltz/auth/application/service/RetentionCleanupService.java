package com.ltz.auth.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.function.IntSupplier;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.ltz.auth.domain.entity.Account;
import com.ltz.auth.domain.enums.AccountStatus;
import com.ltz.auth.domain.enums.SecurityEventType;
import com.ltz.auth.infrastructure.config.CleanupProperties;
import com.ltz.auth.infrastructure.persistence.repository.AccountRepository;
import com.ltz.auth.infrastructure.persistence.repository.AuthDataEraser;
import com.ltz.auth.infrastructure.persistence.repository.RetentionCleanupRepository;

import lombok.RequiredArgsConstructor;

/**
 * Süresi dolan geçici kayıtları ve doğrulanmamış hesapları temizler. İdempotenttir ve çoklu örnekte güvenlidir
 * (silmeler sınırlı ve tekrarlanabilir; bekleyen hesaplar SKIP LOCKED ile alınır).
 * <p>
 * Doğrulanmamış hesap silinmez, <b>anonimleştirilir</b>: e-posta/kullanıcı adı serbest kalır, oturum ve token'ları
 * silinir, ancak güvenlik olayları ve denetim kayıtları korunur (saklama süresi kararı verilmeden silinmez).
 * Bu hesaplar hiç ACTIVE olmadığı için diğer servislere bildirilmiş veri yoktur.
 */
@Service
@RequiredArgsConstructor
public class RetentionCleanupService {

    /** Bir çalıştırmada her tablo için işlenebilecek en fazla parti sayısı (tek turun sınırsız sürmesini önler). */
    static final int MAX_BATCHES_PER_RUN = 50;

    private final RetentionCleanupRepository cleanupRepository;
    private final AccountRepository accountRepository;
    private final AuthDataEraser authDataEraser;
    private final SecurityEventRecorder securityEventRecorder;
    private final CleanupProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public CleanupResult run() {
        Instant now = clock.instant();
        int batch = properties.batchSize();
        int pendingAccounts = purgePendingAccounts(now);
        int ephemeral = drain(() -> cleanupRepository.deleteExpiredOAuthFlows(now.minus(properties.flowGrace()), batch))
                + drain(() -> cleanupRepository.deleteOldOpenIdNonces(now.minus(properties.nonceRetention()), batch))
                + drain(() -> cleanupRepository.deleteExpiredSteamProofs(now.minus(properties.flowGrace()), batch))
                + drain(() -> cleanupRepository.deleteStaleThrottles(now.minus(properties.throttleRetention()), batch))
                + drain(() -> cleanupRepository.deleteExpiredVerificationCodes(now.minus(properties.tokenRetention()), batch))
                + drain(() -> cleanupRepository.deleteExpiredPasswordResetTokens(now.minus(properties.tokenRetention()), batch))
                + drain(() -> cleanupRepository.deleteExpiredDeletionTokens(now.minus(properties.tokenRetention()), batch))
                + drain(() -> cleanupRepository.deleteExpiredMfaChallenges(now.minus(properties.tokenRetention()), batch))
                + drain(() -> cleanupRepository.deleteExpiredSessions(now.minus(properties.sessionRetention()), batch));
        int securityEvents = properties.securityEventRetentionConfigured()
                ? drain(() -> cleanupRepository.deleteOldSecurityEvents(
                        now.minus(properties.securityEventRetention()), batch))
                : 0;
        return new CleanupResult(pendingAccounts, ephemeral, securityEvents);
    }

    private int purgePendingAccounts(Instant now) {
        int total = 0;
        for (int round = 0; round < MAX_BATCHES_PER_RUN; round++) {
            Integer purged = transactionTemplate.execute(status -> purgePendingBatch(now));
            total += purged;
            if (purged < properties.batchSize()) {
                break;
            }
        }
        return total;
    }

    private int purgePendingBatch(Instant now) {
        List<Account> expired = accountRepository.findStaleForUpdate(AccountStatus.PENDING_VERIFICATION,
                now.minus(properties.pendingAccountTtl()), PageRequest.of(0, properties.batchSize()));
        for (Account account : expired) {
            authDataEraser.erase(account.getId());
            account.anonymize(now);
            securityEventRecorder.recordEvent(SecurityEventType.PENDING_ACCOUNT_EXPIRED, account.getId());
        }
        return expired.size();
    }

    private int drain(IntSupplier batchDelete) {
        int total = 0;
        for (int round = 0; round < MAX_BATCHES_PER_RUN; round++) {
            int deleted = batchDelete.getAsInt();
            total += deleted;
            if (deleted < properties.batchSize()) {
                break;
            }
        }
        return total;
    }

    public record CleanupResult(int expiredPendingAccounts, int ephemeralRows, int securityEvents) {

        public int total() {
            return expiredPendingAccounts + ephemeralRows + securityEvents;
        }
    }
}

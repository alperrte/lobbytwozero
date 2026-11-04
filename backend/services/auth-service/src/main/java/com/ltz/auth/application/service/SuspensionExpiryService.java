package com.ltz.auth.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.auth.application.port.SecurityNotificationType;
import com.ltz.auth.domain.entity.Account;
import com.ltz.auth.domain.entity.AccountStatusHistory;
import com.ltz.auth.domain.enums.AccountStatus;
import com.ltz.auth.domain.enums.AuthEventType;
import com.ltz.auth.domain.enums.SecurityEventType;
import com.ltz.auth.infrastructure.config.SuspensionProperties;
import com.ltz.auth.infrastructure.persistence.repository.AccountRepository;
import com.ltz.auth.infrastructure.persistence.repository.AccountStatusHistoryRepository;

import lombok.RequiredArgsConstructor;

/**
 * Süreli hesap askısının (B8) bitişi: {@code suspended_until} gelen SUSPENDED hesaplar ACTIVE olur. Satırlar SKIP LOCKED
 * ile kilitlenir; çok örnekte aynı hesap iki kez açılmaz. Denetim kaydında işlemi yapan sabit sistem kimliğidir
 * ({@link #SYSTEM_ACTOR}); kullanıcıya yönetici elle açmış gibi "hesap yeniden etkinleştirildi" bildirimi gider ve diğer
 * servislere {@code AccountStatusChanged(ACTIVE)} olayı yayımlanır.
 */
@Service
@RequiredArgsConstructor
public class SuspensionExpiryService {

    /** Otomatik işlemlerin denetim kaydındaki kimliği (hiçbir hesapla eşleşmeyen sıfır UUID). */
    public static final UUID SYSTEM_ACTOR = new UUID(0L, 0L);
    static final String REASON = "Süreli askı süresi doldu";

    private final AccountRepository accountRepository;
    private final AccountStatusHistoryRepository statusHistoryRepository;
    private final SecurityEventRecorder securityEventRecorder;
    private final AuthEventPublisher eventPublisher;
    private final SecurityNotificationDispatcher notifier;
    private final SuspensionProperties properties;
    private final Clock clock;

    /** Süresi dolan askıları açar; bir turda en çok {@code batchSize} hesap. Açılan hesap sayısını döner. */
    @Transactional
    public int expireDue() {
        Instant now = clock.instant();
        List<Account> due = accountRepository.findSuspensionsDueForUpdate(now, PageRequest.of(0, properties.batchSize()));
        for (Account account : due) {
            account.changeStatus(AccountStatus.ACTIVE, now);
            statusHistoryRepository.save(AccountStatusHistory.of(account.getId(), AccountStatus.SUSPENDED,
                    AccountStatus.ACTIVE, REASON, SYSTEM_ACTOR, now));
            securityEventRecorder.recordEvent(SecurityEventType.ACCOUNT_STATUS_CHANGED, account.getId());
            eventPublisher.publish(AuthEventType.ACCOUNT_STATUS_CHANGED, account.getId(), AccountStatus.ACTIVE.name());
            notifier.notifyAfterCommit(account.getId(), account.getEmail(), SecurityNotificationType.ACCOUNT_REACTIVATED);
        }
        return due.size();
    }
}

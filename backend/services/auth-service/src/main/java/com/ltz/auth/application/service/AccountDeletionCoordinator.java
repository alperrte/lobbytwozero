package com.ltz.auth.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.ltz.auth.application.port.AccountDataEraserPort;
import com.ltz.auth.application.port.AccountErasureException;
import com.ltz.auth.domain.entity.Account;
import com.ltz.auth.domain.entity.AccountDeletionRequest;
import com.ltz.auth.domain.entity.AccountDeletionTask;
import com.ltz.auth.domain.enums.AccountStatus;
import com.ltz.auth.domain.enums.DeletionTaskStatus;
import com.ltz.auth.domain.enums.SecurityEventType;
import com.ltz.auth.infrastructure.config.DeletionProperties;
import com.ltz.auth.infrastructure.persistence.repository.AccountDeletionRequestRepository;
import com.ltz.auth.infrastructure.persistence.repository.AccountDeletionTaskRepository;
import com.ltz.auth.infrastructure.persistence.repository.AccountRepository;
import com.ltz.auth.infrastructure.persistence.repository.AuthDataEraser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Silme koordinasyonu (AUTH-16/17). Her veri sahibi servis için bir görev vardır; görev, servis silmeyi idempotent
 * olarak onayladığında CONFIRMED olur. Güvenlik özellikleri:
 * <ul>
 *   <li><b>Kira + SKIP LOCKED:</b> görev kısa bir işlemde "kiralanır", HTTP çağrısı işlem DIŞINDA yapılır; eşzamanlı
 *       işlemciler aynı görevi almaz, çöken işlemci görevi kaybettirmez (kira dolunca yeniden denenir).</li>
 *   <li><b>Kısmi başarısızlık:</b> yalnız başarısız görev yeniden denenir (üstel geri çekilme); onaylanmış görevler
 *       tekrarlanmaz.</li>
 *   <li><b>Tamamlama:</b> son görev onaylanınca, aynı işlemde talep kilitli olarak, Auth'un kendi kişisel verisi silinir
 *       ve hesap DELETED + anonim olur. Hiçbir servis onaylamadan hesap DELETED sayılmaz.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountDeletionCoordinator {

    private final AccountDeletionTaskRepository taskRepository;
    private final AccountDeletionRequestRepository requestRepository;
    private final AccountRepository accountRepository;
    private final AccountDataEraserPort eraser;
    private final AuthDataEraser authDataEraser;
    private final SecurityEventRecorder securityEventRecorder;
    private final DeletionProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /** @return bu turda onaylanan görev sayısı */
    public int processDue() {
        List<ClaimedTask> claimed = transactionTemplate.execute(status -> claimDue());
        int confirmed = 0;
        for (ClaimedTask task : claimed) {
            try {
                eraser.erase(task.service(), task.requestId(), task.accountId());
            } catch (AccountErasureException exception) {
                recordFailure(task, exception.code());
                continue;
            }
            recordConfirmation(task);
            confirmed++;
        }
        return confirmed;
    }

    /**
     * Görev ancak bekleme süresi bitince (next_attempt_at = scheduledFor) vadesi gelir. Vadesi gelen ilk görevle birlikte
     * hesabın Auth PII'si hemen anonimleştirilir: servislerin silmeyi onaylaması süresiz sürse bile e-posta, kullanıcı adı,
     * parola ve 2FA sırları Auth'ta kalmaz. Hesap kimliği ve durum (DELETION_REQUESTED) korunur.
     */
    private List<ClaimedTask> claimDue() {
        Instant now = clock.instant();
        List<AccountDeletionTask> due = taskRepository.findDue(DeletionTaskStatus.PENDING, now,
                PageRequest.of(0, properties.batchSize()));
        Set<UUID> accounts = new LinkedHashSet<>();
        for (AccountDeletionTask task : due) {
            task.claim(now, properties.lease());
            accounts.add(task.getAccountId());
        }
        accounts.forEach(accountId -> anonymizeIdentity(accountId, now));
        return due.stream()
                .map(task -> new ClaimedTask(task.getId(), task.getRequestId(), task.getAccountId(), task.getService()))
                .toList();
    }

    private void anonymizeIdentity(UUID accountId, Instant now) {
        accountRepository.findByIdForUpdate(accountId)
                .filter(account -> account.getStatus() == AccountStatus.DELETION_REQUESTED
                        && !account.isIdentityAnonymized())
                .ifPresent(account -> {
                    authDataEraser.erase(accountId);
                    account.anonymizeIdentity(now);
                    securityEventRecorder.recordEvent(SecurityEventType.ACCOUNT_DELETION_IDENTITY_ERASED, accountId);
                });
    }

    private void recordFailure(ClaimedTask claimed, String errorCode) {
        transactionTemplate.executeWithoutResult(status -> taskRepository.findById(claimed.taskId())
                .filter(AccountDeletionTask::isPending)
                .ifPresent(task -> {
                    task.fail(clock.instant(), errorCode, properties.initialBackoff(), properties.maxBackoff());
                    // Her denemede olay yazmak denetim tablosunu şişirir: yalnız ilk hata ve takılma eşiğinde.
                    if (task.getAttempts() == 1 || task.getAttempts() == properties.stuckAttempts()) {
                        securityEventRecorder.recordEvent(SecurityEventType.ACCOUNT_DELETION_TASK_FAILED, task.getAccountId());
                    }
                    if (task.getAttempts() == properties.stuckAttempts()) {
                        log.warn("Account deletion task stuck service={} attempts={} error={}", task.getService(),
                                task.getAttempts(), errorCode);
                    }
                }));
    }

    private void recordConfirmation(ClaimedTask claimed) {
        transactionTemplate.executeWithoutResult(status -> {
            // Talep satırı kilidi: görevler eşzamanlı onaylansa da yalnız son onaylayan tamamlamayı yapar.
            AccountDeletionRequest request = requestRepository.findByIdForUpdate(claimed.requestId()).orElseThrow();
            taskRepository.findById(claimed.taskId()).filter(AccountDeletionTask::isPending)
                    .ifPresent(task -> task.confirm(clock.instant()));
            taskRepository.flush();
            if (!request.isCompleted()
                    && taskRepository.countByRequestIdAndStatus(request.getId(), DeletionTaskStatus.PENDING) == 0) {
                finish(request);
            }
        });
    }

    private void finish(AccountDeletionRequest request) {
        Instant now = clock.instant();
        Account account = accountRepository.findByIdForUpdate(request.getAccountId()).orElseThrow();
        if (account.getStatus() == AccountStatus.DELETION_REQUESTED) {
            authDataEraser.erase(account.getId());
            account.anonymize(now);
        }
        request.complete(now);
        securityEventRecorder.recordEvent(SecurityEventType.ACCOUNT_DELETION_COMPLETED, request.getAccountId());
    }

    private record ClaimedTask(UUID taskId, UUID requestId, UUID accountId, String service) {
    }
}

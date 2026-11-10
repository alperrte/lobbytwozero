package com.ltz.auth.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.ltz.auth.domain.entity.Account;
import com.ltz.auth.domain.entity.DeletionSupportRequest;
import com.ltz.auth.domain.enums.AccountTier;
import com.ltz.auth.domain.enums.DeletionSupportStatus;
import com.ltz.auth.domain.enums.RoleCode;
import com.ltz.auth.domain.enums.SecurityEventType;
import com.ltz.auth.domain.exception.AuthErrorCode;
import com.ltz.auth.domain.exception.AuthException;
import com.ltz.auth.domain.exception.RedactedCause;
import com.ltz.auth.infrastructure.persistence.repository.AccountRepository;
import com.ltz.auth.infrastructure.persistence.repository.DeletionSupportRequestRepository;

import lombok.RequiredArgsConstructor;

/**
 * B ve C türü hesabın silme talebi. Bu hesaplar {@code deletion/request} ile kendi başına silme başlatamaz; yöneticiye
 * talep açar. Yönetici onaylarsa standart silme süreci başlar (bekleme süresi, servis görevleri; kullanıcı onay
 * e-postası gerekmez), reddederse hesap olduğu gibi kalır. A türü bu yolu kullanmaz, doğrudan kendi siler.
 * Hesap başına aynı anda tek bekleyen talep vardır; ikinci istek mevcut talebi döner (idempotent).
 */
@Service
@RequiredArgsConstructor
public class DeletionSupportService {

    private final AccountRepository accountRepository;
    private final DeletionSupportRequestRepository requestRepository;
    private final AccountTierService tierService;
    private final AccountDeletionService deletionService;
    private final SecurityEventRecorder securityEventRecorder;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public DeletionSupportRequest submit(UUID accountId, String message) {
        String text = normalize(message);
        try {
            return transactionTemplate.execute(status -> open(accountId, text));
        } catch (DataIntegrityViolationException exception) {
            // Eşzamanlı ikinci istek: tekil bekleyen talep indeksi çakıştı; kazanan talep döner.
            return requestRepository.findFirstByAccountIdAndStatus(accountId, DeletionSupportStatus.PENDING)
                    .orElseThrow(() -> new AuthException(AuthErrorCode.SESSION_INVALID, RedactedCause.of(exception)));
        }
    }

    private DeletionSupportRequest open(UUID accountId, String message) {
        Account account = accountRepository.findWithRolesById(accountId).filter(Account::isActive)
                .orElseThrow(() -> new AuthException(AuthErrorCode.SESSION_INVALID));
        if (account.roleCodes().contains(RoleCode.ADMIN)) {
            throw new AuthException(AuthErrorCode.ADMIN_DELETION_FORBIDDEN);
        }
        if (tierService.tierOf(account) == AccountTier.A) {
            throw new AuthException(AuthErrorCode.DELETION_SELF_SERVICE_AVAILABLE);
        }
        return requestRepository.findFirstByAccountIdAndStatus(accountId, DeletionSupportStatus.PENDING)
                .orElseGet(() -> {
                    DeletionSupportRequest created = requestRepository.saveAndFlush(
                            DeletionSupportRequest.open(accountId, message, clock.instant()));
                    securityEventRecorder.recordEvent(SecurityEventType.DELETION_SUPPORT_REQUESTED, accountId);
                    return created;
                });
    }

    /** Hesabın en son talebi (bekleyen ya da karara bağlanmış); hiç talep yoksa boş. */
    public Optional<DeletionSupportRequest> latest(UUID accountId) {
        return requestRepository.findFirstByAccountIdOrderByCreatedAtDesc(accountId);
    }

    /** Bekleyen talepler, en eski önce. Kişisel veri yok; hesap kimliği ve kullanıcının yazdığı kısa metin vardır. */
    public List<DeletionSupportRequest> pending(int limit) {
        return requestRepository.findByStatusOrderByCreatedAtAsc(DeletionSupportStatus.PENDING, PageRequest.of(0, limit));
    }

    /** Onay: talep ve hesap kilitlenir, standart silme başlar. Hesap artık aktif değilse ya da yöneticiyse onaylanmaz. */
    public DeletionSupportRequest approve(UUID adminId, UUID requestId, String note) {
        return transactionTemplate.execute(status -> {
            Instant now = clock.instant();
            DeletionSupportRequest request = pendingRequest(requestId);
            Account account = accountRepository.findByIdForUpdate(request.getAccountId())
                    .filter(Account::isActive)
                    .orElseThrow(() -> new AuthException(AuthErrorCode.TARGET_NOT_ACTIVE));
            if (account.roleCodes().contains(RoleCode.ADMIN)) {
                throw new AuthException(AuthErrorCode.ADMIN_DELETION_FORBIDDEN);
            }
            request.approve(adminId, normalize(note), now);
            deletionService.beginDeletion(account, now);
            securityEventRecorder.recordEvent(SecurityEventType.DELETION_SUPPORT_APPROVED, account.getId());
            return request;
        });
    }

    /** Ret: hesap değişmez, kullanıcı gerekirse yeni talep açabilir. */
    public DeletionSupportRequest reject(UUID adminId, UUID requestId, String note) {
        return transactionTemplate.execute(status -> {
            DeletionSupportRequest request = pendingRequest(requestId);
            request.reject(adminId, normalize(note), clock.instant());
            securityEventRecorder.recordEvent(SecurityEventType.DELETION_SUPPORT_REJECTED, request.getAccountId());
            return request;
        });
    }

    private DeletionSupportRequest pendingRequest(UUID requestId) {
        DeletionSupportRequest request = requestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.DELETION_SUPPORT_REQUEST_NOT_FOUND));
        if (!request.isPending()) {
            throw new AuthException(AuthErrorCode.DELETION_SUPPORT_ALREADY_DECIDED);
        }
        return request;
    }

    private static String normalize(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String stripped = text.strip();
        return stripped.length() > DeletionSupportRequest.TEXT_MAX_LENGTH
                ? stripped.substring(0, DeletionSupportRequest.TEXT_MAX_LENGTH) : stripped;
    }
}

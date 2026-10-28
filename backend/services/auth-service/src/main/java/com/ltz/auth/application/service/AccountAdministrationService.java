package com.ltz.auth.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.auth.domain.entity.Account;
import com.ltz.auth.domain.entity.AccountRoleHistory;
import com.ltz.auth.domain.entity.AccountStatusHistory;
import com.ltz.auth.domain.enums.AccountStatus;
import com.ltz.auth.domain.enums.AuthEventType;
import com.ltz.auth.domain.enums.DeviceRevokeReason;
import com.ltz.auth.domain.enums.RoleAction;
import com.ltz.auth.domain.enums.RoleCode;
import com.ltz.auth.domain.enums.SecurityEventType;
import com.ltz.auth.domain.enums.SessionRevokeReason;
import com.ltz.auth.domain.exception.AuthErrorCode;
import com.ltz.auth.domain.exception.AuthException;
import com.ltz.auth.infrastructure.persistence.repository.AccountRepository;
import com.ltz.auth.infrastructure.persistence.repository.AccountRoleHistoryRepository;
import com.ltz.auth.infrastructure.persistence.repository.AccountStatusHistoryRepository;
import com.ltz.auth.infrastructure.persistence.repository.AuthSessionRepository;
import com.ltz.auth.infrastructure.persistence.repository.RoleRepository;

import lombok.RequiredArgsConstructor;

/**
 * AUTH-11 rol yönetimi ve AUTH-12 hesap durumları (yalnız ADMIN; yetki SecurityConfig'te zorunlu kılınır).
 * <ul>
 *   <li>Yetki yükseltme yok: yönetici kendi rolünü/durumunu değiştiremez; PLAYER temel rolü alınamaz.</li>
 *   <li>Zorunlu 2FA rolleri (ADMIN, MODERATOR) yalnız TOTP'si etkin ve ACTIVE hesaba verilir.</li>
 *   <li>Her değişiklik gerekçe + yönetici kimliğiyle denetim tablosuna yazılır.</li>
 *   <li>Rol veya durum değişince hesabın oturumları iptal edilir ve token sürümü artar; böylece eski rol/durum
 *       taşıyan access token'lar auth uçlarında anında düşer.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AccountAdministrationService {

    private final AccountRepository accountRepository;
    private final RoleRepository roleRepository;
    private final AuthSessionRepository sessionRepository;
    private final DeviceRevoker deviceRevoker;
    private final AccountStatusHistoryRepository statusHistoryRepository;
    private final AccountRoleHistoryRepository roleHistoryRepository;
    private final TwoFactorService twoFactorService;
    private final SecurityEventRecorder securityEventRecorder;
    private final AuthEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AccountAdminView get(UUID accountId) {
        Account account = accountRepository.findWithRolesById(accountId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.ACCOUNT_NOT_FOUND));
        return new AccountAdminView(account.getId(), account.getUsername(), account.getStatus(),
                account.roleCodes().stream().map(Enum::name).sorted().toList(),
                twoFactorService.isEnabled(accountId), account.getCreatedAt());
    }

    @Transactional
    public void changeStatus(UUID actorId, UUID accountId, AccountStatus target, String reason) {
        forbidSelfModification(actorId, accountId);
        Account account = lock(accountId);
        AccountStatus from = account.getStatus();
        if (!from.canTransitionTo(target)) {
            throw new AuthException(AuthErrorCode.INVALID_STATUS_TRANSITION);
        }
        Instant now = clock.instant();
        account.changeStatus(target, now);
        statusHistoryRepository.save(AccountStatusHistory.of(accountId, from, target, reason, actorId, now));
        // Toplu güncelleme persistence context'i temizler; bekleyen değişiklikler önce flush edilir.
        if (target != AccountStatus.ACTIVE) {
            sessionRepository.revokeAllActive(accountId, SessionRevokeReason.ACCOUNT_INACTIVE, now);
            deviceRevoker.revokeAll(accountId, DeviceRevokeReason.ACCOUNT_INACTIVE, now);
        }
        securityEventRecorder.recordEvent(SecurityEventType.ACCOUNT_STATUS_CHANGED, accountId);
        eventPublisher.publish(AuthEventType.ACCOUNT_STATUS_CHANGED, accountId, target.name());
    }

    @Transactional
    public void grantRole(UUID actorId, UUID accountId, RoleCode role, String reason) {
        forbidSelfModification(actorId, accountId);
        Account account = lock(accountId);
        if (!account.isActive()) {
            throw new AuthException(AuthErrorCode.TARGET_NOT_ACTIVE);
        }
        if (account.roleCodes().contains(role)) {
            throw new AuthException(AuthErrorCode.ROLE_ALREADY_ASSIGNED);
        }
        if (twoFactorService.isRequiredFor(Set.of(role)) && !twoFactorService.isEnabled(accountId)) {
            throw new AuthException(AuthErrorCode.ROLE_REQUIRES_TWO_FACTOR);
        }
        Instant now = clock.instant();
        account.grantRole(roleRepository.findByCode(role)
                .orElseThrow(() -> new IllegalStateException("Role seed is missing: " + role)), now);
        finishRoleChange(actorId, accountId, role, RoleAction.GRANT, reason, now);
        securityEventRecorder.recordEvent(SecurityEventType.ACCOUNT_ROLE_GRANTED, accountId);
    }

    @Transactional
    public void revokeRole(UUID actorId, UUID accountId, RoleCode role, String reason) {
        forbidSelfModification(actorId, accountId);
        if (role == RoleCode.PLAYER) {
            throw new AuthException(AuthErrorCode.BASE_ROLE_REQUIRED);
        }
        Account account = lock(accountId);
        if (!account.roleCodes().contains(role)) {
            throw new AuthException(AuthErrorCode.ROLE_NOT_ASSIGNED);
        }
        Instant now = clock.instant();
        account.revokeRole(role, now);
        finishRoleChange(actorId, accountId, role, RoleAction.REVOKE, reason, now);
        securityEventRecorder.recordEvent(SecurityEventType.ACCOUNT_ROLE_REVOKED, accountId);
    }

    private void finishRoleChange(UUID actorId, UUID accountId, RoleCode role, RoleAction action, String reason,
                                  Instant now) {
        roleHistoryRepository.save(AccountRoleHistory.of(accountId, role, action, reason, actorId, now));
        sessionRepository.revokeAllActive(accountId, SessionRevokeReason.ROLE_CHANGED, now);
    }

    /** Yönetici kendini kilitleyemez veya yetkisini değiştiremez (son yönetici kaybı ve yetki yükseltme önlenir). */
    private static void forbidSelfModification(UUID actorId, UUID accountId) {
        if (actorId.equals(accountId)) {
            throw new AuthException(AuthErrorCode.SELF_MODIFICATION_FORBIDDEN);
        }
    }

    private Account lock(UUID accountId) {
        return accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.ACCOUNT_NOT_FOUND));
    }
}

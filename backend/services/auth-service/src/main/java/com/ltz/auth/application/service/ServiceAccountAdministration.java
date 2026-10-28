package com.ltz.auth.application.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.auth.domain.entity.Account;
import com.ltz.auth.domain.enums.AccountStatus;
import com.ltz.auth.domain.enums.RoleCode;
import com.ltz.auth.domain.exception.AuthErrorCode;
import com.ltz.auth.domain.exception.AuthException;
import com.ltz.auth.infrastructure.persistence.repository.AccountRepository;

import lombok.RequiredArgsConstructor;

/**
 * Servis kimlikli (admin-service) hesap yönetimi. Servis token'ı kullanıcı yetkisi taşımaz; işlemi yapan yönetici
 * ({@code actorId}) bu serviste yeniden doğrulanır: hesap var, ACTIVE ve ADMIN rolünde olmalıdır. Aksi halde işlem
 * yapılmaz ({@code ACTING_ADMIN_INVALID}). Asıl kurallar (kendini değiştirme yasağı, 2FA'lı rol, durum geçişleri, oturum
 * iptali, denetim kaydı) kullanıcı uçlarıyla aynı {@link AccountAdministrationService} üzerinden uygulanır; Auth hiçbir
 * kuralı servis çağrısı için gevşetmez.
 */
@Service
@RequiredArgsConstructor
public class ServiceAccountAdministration {

    private final AccountAdministrationService administrationService;
    private final AccountRepository accountRepository;

    public AccountAdminView get(UUID accountId) {
        return administrationService.get(accountId);
    }

    @Transactional
    public void changeStatus(UUID actorId, UUID accountId, AccountStatus target, String reason) {
        requireActiveAdmin(actorId);
        administrationService.changeStatus(actorId, accountId, target, reason);
    }

    @Transactional
    public void grantRole(UUID actorId, UUID accountId, RoleCode role, String reason) {
        requireActiveAdmin(actorId);
        administrationService.grantRole(actorId, accountId, role, reason);
    }

    @Transactional
    public void revokeRole(UUID actorId, UUID accountId, RoleCode role, String reason) {
        requireActiveAdmin(actorId);
        administrationService.revokeRole(actorId, accountId, role, reason);
    }

    private void requireActiveAdmin(UUID actorId) {
        boolean valid = accountRepository.findWithRolesById(actorId)
                .filter(Account::isActive)
                .map(actor -> actor.roleCodes().contains(RoleCode.ADMIN))
                .orElse(false);
        if (!valid) {
            throw new AuthException(AuthErrorCode.ACTING_ADMIN_INVALID);
        }
    }
}

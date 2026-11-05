package com.ltz.auth.application.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ltz.auth.domain.entity.Account;
import com.ltz.auth.domain.enums.AccountStatus;

/**
 * Yönetici görünümü: kimlik, durum, roller ve 2FA durumu. {@code suspendedUntil}: süreli askının bitişi (süresiz veya
 * askıda değilse null). E-posta ve parola bilgisi içermez (veri minimizasyonu).
 */
public record AccountAdminView(UUID id, String username, AccountStatus status, List<String> roles,
                               boolean twoFactorEnabled, Instant createdAt, Instant suspendedUntil) {

    public AccountAdminView {
        roles = List.copyOf(roles);
    }

    /** Hesabın yönetici görünümü; roller ada göre sıralı, e-posta ve parola özeti hiçbir alanda yoktur. */
    public static AccountAdminView of(Account account, boolean twoFactorEnabled) {
        return new AccountAdminView(account.getId(), account.getUsername(), account.getStatus(),
                account.roleCodes().stream().map(Enum::name).sorted().toList(), twoFactorEnabled,
                account.getCreatedAt(), account.getSuspendedUntil());
    }
}

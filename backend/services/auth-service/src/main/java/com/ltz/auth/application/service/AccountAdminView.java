package com.ltz.auth.application.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ltz.auth.domain.enums.AccountStatus;

/** Yönetici görünümü: kimlik, durum, roller ve 2FA durumu. E-posta ve parola bilgisi içermez (veri minimizasyonu). */
public record AccountAdminView(UUID id, String username, AccountStatus status, List<String> roles,
                               boolean twoFactorEnabled, Instant createdAt) {

    public AccountAdminView {
        roles = List.copyOf(roles);
    }
}

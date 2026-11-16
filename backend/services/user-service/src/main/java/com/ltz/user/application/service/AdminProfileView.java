package com.ltz.user.application.service;

import java.time.Instant;
import java.util.UUID;

import com.ltz.user.domain.enums.AccountStatus;

/** Yönetim paneli arama satırı: hesap kimliği (= {@code userId}), adlar ve User'daki hesap durumu kopyası. */
public record AdminProfileView(UUID userId, String username, String displayName, AccountStatus accountStatus,
        Instant memberSince) {
}

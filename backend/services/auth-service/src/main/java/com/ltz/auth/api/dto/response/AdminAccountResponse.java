package com.ltz.auth.api.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ltz.auth.application.service.AccountAdminView;
import com.ltz.auth.domain.enums.AccountStatus;

public record AdminAccountResponse(UUID id, String username, AccountStatus status, List<String> roles,
                                   boolean twoFactorEnabled, Instant createdAt) {

    public AdminAccountResponse {
        roles = List.copyOf(roles);
    }

    public static AdminAccountResponse from(AccountAdminView view) {
        return new AdminAccountResponse(view.id(), view.username(), view.status(), view.roles(),
                view.twoFactorEnabled(), view.createdAt());
    }
}

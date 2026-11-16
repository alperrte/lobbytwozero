package com.ltz.user.api.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ltz.user.application.service.AdminProfileView;

/** Yönetim paneli arama sonucu: panel {@code userId}'yi Auth'taki hesap kimliği olarak kullanır. */
public record AdminProfileSearchResponse(List<Item> items) {

    public record Item(UUID userId, String username, String displayName, String accountStatus, Instant memberSince) {
    }

    public static AdminProfileSearchResponse from(List<AdminProfileView> views) {
        return new AdminProfileSearchResponse(views.stream()
                .map(view -> new Item(view.userId(), view.username(), view.displayName(),
                        view.accountStatus() == null ? null : view.accountStatus().name(), view.memberSince()))
                .toList());
    }
}

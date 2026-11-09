package com.ltz.auth.api.dto.response;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ltz.auth.application.service.AccountContactView;
import com.ltz.auth.domain.enums.AccountStatus;

/** {@code email}: adresi olmayan hesapta açıkça {@code null} döner (alan her zaman vardır). */
public record AccountContactResponse(UUID accountId, AccountStatus status,
                                     @JsonInclude(JsonInclude.Include.ALWAYS) String email, boolean emailVerified) {

    public static AccountContactResponse from(AccountContactView view) {
        return new AccountContactResponse(view.accountId(), view.status(), view.email(), view.emailVerified());
    }

    @Override
    public String toString() {
        return "AccountContactResponse[accountId=" + accountId + ", status=" + status + ", email=***]";
    }
}

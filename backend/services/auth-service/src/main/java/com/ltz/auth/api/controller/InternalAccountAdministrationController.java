package com.ltz.auth.api.controller;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.auth.api.dto.request.InternalRoleChangeRequest;
import com.ltz.auth.api.dto.request.InternalStatusChangeRequest;
import com.ltz.auth.api.dto.response.AdminAccountResponse;
import com.ltz.auth.application.service.ServiceAccountAdministration;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Servis kimlikli hesap yönetimi (B4: admin-service → Auth). Yetki SecurityConfig'te kapsam ile zorunlu kılınır
 * ({@code account:read}, {@code account:status:write}, {@code account:role:write}); servis token'ı kullanıcı yetkisi taşımaz,
 * işlemi yapan yönetici gövdedeki {@code actorId} ile bildirilir ve Auth'ta yeniden doğrulanır. Kurallar kullanıcı
 * uçlarıyla aynıdır. {@code /internal/**} Gateway'e açılmaz.
 */
@RestController
@RequiredArgsConstructor
public class InternalAccountAdministrationController {

    private static final Logger log = LoggerFactory.getLogger(InternalAccountAdministrationController.class);

    private final ServiceAccountAdministration administration;

    @GetMapping(AuthApiPaths.INTERNAL_ACCOUNT)
    public AdminAccountResponse account(@PathVariable UUID accountId) {
        return AdminAccountResponse.from(administration.get(accountId));
    }

    @PostMapping(AuthApiPaths.INTERNAL_ACCOUNT_STATUS)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeStatus(@AuthenticationPrincipal Jwt serviceToken, @PathVariable UUID accountId,
                             @Valid @RequestBody InternalStatusChangeRequest request) {
        administration.changeStatus(request.actorId(), accountId, request.status(), request.reason());
        log.info("Internal account status change caller={} account={}", serviceToken.getSubject(), accountId);
    }

    @PostMapping(AuthApiPaths.INTERNAL_ACCOUNT_ROLES_GRANT)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void grantRole(@AuthenticationPrincipal Jwt serviceToken, @PathVariable UUID accountId,
                          @Valid @RequestBody InternalRoleChangeRequest request) {
        administration.grantRole(request.actorId(), accountId, request.role(), request.reason());
        log.info("Internal role grant caller={} account={}", serviceToken.getSubject(), accountId);
    }

    @PostMapping(AuthApiPaths.INTERNAL_ACCOUNT_ROLES_REVOKE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeRole(@AuthenticationPrincipal Jwt serviceToken, @PathVariable UUID accountId,
                           @Valid @RequestBody InternalRoleChangeRequest request) {
        administration.revokeRole(request.actorId(), accountId, request.role(), request.reason());
        log.info("Internal role revoke caller={} account={}", serviceToken.getSubject(), accountId);
    }
}

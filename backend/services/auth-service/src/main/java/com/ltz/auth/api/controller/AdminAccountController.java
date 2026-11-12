package com.ltz.auth.api.controller;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.auth.api.dto.request.AgeRevocationRequest;
import com.ltz.auth.api.dto.request.DeletionSupportMessageRequest;
import com.ltz.auth.api.dto.request.RoleChangeRequest;
import com.ltz.auth.api.dto.request.StatusChangeRequest;
import com.ltz.auth.api.dto.response.AdminAccountResponse;
import com.ltz.auth.api.dto.response.AdminDeletionSupportResponse;
import com.ltz.auth.api.dto.response.PendingDeletionTaskResponse;
import com.ltz.auth.application.service.AccountAdministrationService;
import com.ltz.auth.application.service.AdultVerificationService;
import com.ltz.auth.application.service.DeletionSupportService;
import com.ltz.auth.application.service.PendingDeletionQueryService;
import com.ltz.auth.domain.enums.RoleCode;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Yönetici uçları (AUTH-11/12/14). Yetki SecurityConfig'te ({@code /api/v1/auth/admin/**} → ROLE_ADMIN) servis
 * sınırında zorunlu kılınır; Gateway'e veya arayüze bırakılmaz. Yönetici kimliği yalnız token'dan alınır.
 */
@RestController
@RequiredArgsConstructor
public class AdminAccountController {

    private static final int MAX_PENDING_LIMIT = 200;

    private final AccountAdministrationService administrationService;
    private final PendingDeletionQueryService pendingDeletionQueryService;
    private final DeletionSupportService deletionSupportService;
    private final AdultVerificationService adultVerificationService;

    @GetMapping(AuthApiPaths.ADMIN_ROLES)
    public List<String> roles() {
        return Arrays.stream(RoleCode.values()).map(Enum::name).toList();
    }

    /** Silme koordinasyonunda bekleyen/takılı görevler; kişisel veri içermez (e-posta, kullanıcı adı yok). */
    @GetMapping(AuthApiPaths.ADMIN_DELETIONS_PENDING)
    public List<PendingDeletionTaskResponse> pendingDeletions(
            @RequestParam(defaultValue = "50") int limit) {
        return pendingDeletionQueryService.pending(Math.clamp(limit, 1, MAX_PENDING_LIMIT)).stream()
                .map(PendingDeletionTaskResponse::from).toList();
    }

    /** B/C türü hesapların bekleyen silme talepleri, en eski önce. */
    @GetMapping(AuthApiPaths.ADMIN_DELETION_REQUESTS)
    public List<AdminDeletionSupportResponse> deletionRequests(@RequestParam(defaultValue = "50") int limit) {
        return deletionSupportService.pending(Math.clamp(limit, 1, MAX_PENDING_LIMIT)).stream()
                .map(AdminDeletionSupportResponse::from).toList();
    }

    /** Onay standart silme sürecini başlatır (bekleme süresi, servis görevleri); hesap oturumları kapanır. */
    @PostMapping(AuthApiPaths.ADMIN_DELETION_REQUEST_APPROVE)
    public AdminDeletionSupportResponse approveDeletionRequest(@AuthenticationPrincipal Jwt jwt,
                                                               @PathVariable UUID requestId,
                                                               @Valid @RequestBody(required = false) DeletionSupportMessageRequest request) {
        return AdminDeletionSupportResponse.from(
                deletionSupportService.approve(actorId(jwt), requestId, request == null ? null : request.message()));
    }

    @PostMapping(AuthApiPaths.ADMIN_DELETION_REQUEST_REJECT)
    public AdminDeletionSupportResponse rejectDeletionRequest(@AuthenticationPrincipal Jwt jwt,
                                                              @PathVariable UUID requestId,
                                                              @Valid @RequestBody(required = false) DeletionSupportMessageRequest request) {
        return AdminDeletionSupportResponse.from(
                deletionSupportService.reject(actorId(jwt), requestId, request == null ? null : request.message()));
    }

    @GetMapping(AuthApiPaths.ADMIN_ACCOUNT)
    public AdminAccountResponse account(@PathVariable UUID accountId) {
        return AdminAccountResponse.from(administrationService.get(accountId));
    }

    @PostMapping(AuthApiPaths.ADMIN_ACCOUNT_STATUS)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID accountId,
                             @Valid @RequestBody StatusChangeRequest request) {
        administrationService.changeStatus(actorId(jwt), accountId, request.status(), request.reason(),
                request.suspendedUntil());
    }

    @PostMapping(AuthApiPaths.ADMIN_ACCOUNT_ROLES_GRANT)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void grantRole(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID accountId,
                          @Valid @RequestBody RoleChangeRequest request) {
        administrationService.grantRole(actorId(jwt), accountId, request.role(), request.reason());
    }

    @PostMapping(AuthApiPaths.ADMIN_ACCOUNT_ROLES_REVOKE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeRole(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID accountId,
                           @Valid @RequestBody RoleChangeRequest request) {
        administrationService.revokeRole(actorId(jwt), accountId, request.role(), request.reason());
    }

    /** Yaş doğrulamasını geri alır; kullanıcı engel kalkana kadar yeniden beyan edemez. Doğrulama yoksa 204 (idempotent). */
    @PostMapping(AuthApiPaths.ADMIN_ACCOUNT_AGE_REVOKE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeAgeVerification(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID accountId,
                                      @Valid @RequestBody AgeRevocationRequest request) {
        adultVerificationService.revokeByAdmin(actorId(jwt), accountId, request.reason());
    }

    /** Yönetici engelini kaldırır; kullanıcı yeniden beyan edebilir. Engel yoksa 204 (idempotent). */
    @PostMapping(AuthApiPaths.ADMIN_ACCOUNT_AGE_LIFT_BLOCK)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void liftAgeVerificationBlock(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID accountId) {
        adultVerificationService.liftAdminBlock(actorId(jwt), accountId);
    }

    private static UUID actorId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}

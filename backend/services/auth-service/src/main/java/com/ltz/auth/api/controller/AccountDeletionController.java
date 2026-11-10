package com.ltz.auth.api.controller;

import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.auth.api.dto.request.CancelAccountDeletionRequest;
import com.ltz.auth.api.dto.request.ConfirmAccountDeletionRequest;
import com.ltz.auth.api.dto.request.DeletionSupportMessageRequest;
import com.ltz.auth.api.dto.request.RequestAccountDeletionRequest;
import com.ltz.auth.api.dto.response.AccountDeletionResponse;
import com.ltz.auth.api.dto.response.DeletionSupportResponse;
import com.ltz.auth.application.service.AccountDeletionService;
import com.ltz.auth.application.service.DeletionSupportService;
import com.ltz.auth.infrastructure.security.TokenClaims;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Hesap kimliği ve oturum yalnız token'dan alınır; başkası adına silme talebi mümkün değildir. */
@RestController
@RequiredArgsConstructor
public class AccountDeletionController {

    private final AccountDeletionService deletionService;
    private final DeletionSupportService supportService;
    private final SessionCookies sessionCookies;

    @PostMapping(AuthApiPaths.ACCOUNT_DELETION_REQUEST)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AccountDeletionResponse request(@AuthenticationPrincipal Jwt jwt,
                                           @Valid @RequestBody RequestAccountDeletionRequest request) {
        deletionService.requestDeletion(UUID.fromString(jwt.getSubject()),
                UUID.fromString(jwt.getClaimAsString(TokenClaims.SESSION_ID)), request.password(), request.code(),
                request.recoveryCode());
        return AccountDeletionResponse.CONFIRM_BY_EMAIL;
    }

    /**
     * B ve C türü hesap kendi başına silme başlatamaz ({@code DELETION_REQUIRES_SUPPORT}); yöneticiye talep açar.
     * Bekleyen talep varsa yenisi açılmaz, mevcut dönülür. A türü bu yolu kullanmaz ({@code DELETION_SELF_SERVICE_AVAILABLE}).
     */
    @PostMapping(AuthApiPaths.ACCOUNT_DELETION_SUPPORT_REQUEST)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DeletionSupportResponse openSupportRequest(@AuthenticationPrincipal Jwt jwt,
                                                      @Valid @RequestBody DeletionSupportMessageRequest request) {
        return DeletionSupportResponse.from(supportService.submit(UUID.fromString(jwt.getSubject()), request.message()));
    }

    /** Hesabın en son silme talebinin durumu; hiç talep yoksa 204. */
    @GetMapping(AuthApiPaths.ACCOUNT_DELETION_SUPPORT_REQUEST)
    public ResponseEntity<DeletionSupportResponse> supportRequestStatus(@AuthenticationPrincipal Jwt jwt) {
        return supportService.latest(UUID.fromString(jwt.getSubject()))
                .map(request -> ResponseEntity.ok().cacheControl(CacheControl.noStore())
                        .body(DeletionSupportResponse.from(request)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * Bekleme süresi içinde vazgeçme (herkese açık, çerez okumaz): kimlik parola ile doğrulanır, oturum açılmaz.
     */
    @PostMapping(AuthApiPaths.ACCOUNT_DELETION_CANCEL)
    public AccountDeletionResponse cancel(@Valid @RequestBody CancelAccountDeletionRequest request) {
        deletionService.cancelDeletion(request.identifier(), request.password(), request.code(), request.recoveryCode());
        return AccountDeletionResponse.DELETION_CANCELLED;
    }

    /** Başarıda tüm oturumlar kapanır; cookie'ler silinir. Silme servisler onayladıkça arka planda tamamlanır. */
    @PostMapping(AuthApiPaths.ACCOUNT_DELETION_CONFIRM)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AccountDeletionResponse confirm(@AuthenticationPrincipal Jwt jwt,
                                           @Valid @RequestBody ConfirmAccountDeletionRequest request,
                                           HttpServletResponse response) {
        deletionService.confirmDeletion(UUID.fromString(jwt.getSubject()), request.token(), request.username());
        sessionCookies.clear(response);
        return AccountDeletionResponse.DELETION_IN_PROGRESS;
    }
}

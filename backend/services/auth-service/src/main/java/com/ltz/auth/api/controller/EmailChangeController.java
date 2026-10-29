package com.ltz.auth.api.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.auth.api.dto.request.ChangeEmailRequest;
import com.ltz.auth.api.dto.request.ConfirmEmailChangeRequest;
import com.ltz.auth.api.dto.response.VerificationPendingResponse;
import com.ltz.auth.application.service.EmailChangeService;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Hesap kimliği yalnız token'dan alınır; başkası adına adres değiştirilemez. */
@RestController
@RequiredArgsConstructor
public class EmailChangeController {

    private final EmailChangeService emailChangeService;
    private final SessionCookies sessionCookies;

    /** Yeni adres başka bir hesaptaysa da yanıt aynıdır; yanıt kodun gönderildiğini iddia etmez. */
    @PostMapping(AuthApiPaths.EMAIL_CHANGE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public VerificationPendingResponse request(@AuthenticationPrincipal Jwt jwt,
                                               @Valid @RequestBody ChangeEmailRequest request) {
        emailChangeService.requestChange(UUID.fromString(jwt.getSubject()), request.newEmail(), request.password(),
                request.code(), request.recoveryCode());
        return VerificationPendingResponse.VERIFY_EMAIL;
    }

    /** Başarıda tüm oturumlar (bu cihaz dahil) kapanır; cookie'ler silinir, yeni adresle yeniden giriş gerekir. */
    @PostMapping(AuthApiPaths.EMAIL_CHANGE_CONFIRM)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirm(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ConfirmEmailChangeRequest request,
                        HttpServletResponse response) {
        emailChangeService.confirmChange(UUID.fromString(jwt.getSubject()), request.code());
        sessionCookies.clear(response);
    }
}

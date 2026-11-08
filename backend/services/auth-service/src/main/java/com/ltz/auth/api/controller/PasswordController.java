package com.ltz.auth.api.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.auth.api.dto.request.ChangePasswordRequest;
import com.ltz.auth.api.dto.request.ForgotPasswordRequest;
import com.ltz.auth.api.dto.request.RequestPasswordSetRequest;
import com.ltz.auth.api.dto.request.ResetPasswordRequest;
import com.ltz.auth.api.dto.request.SetPasswordRequest;
import com.ltz.auth.api.dto.response.PasswordResetPendingResponse;
import com.ltz.auth.api.dto.response.VerificationPendingResponse;
import com.ltz.auth.application.service.PasswordService;
import com.ltz.auth.application.service.PasswordSetService;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class PasswordController {

    private final PasswordService passwordService;
    private final PasswordSetService passwordSetService;
    private final SessionCookies sessionCookies;

    /** Başarılı değişiklikte tüm oturumlar (bu cihaz dahil) kapanır; cookie'ler silinir, yeniden giriş gerekir. */
    @PostMapping(AuthApiPaths.PASSWORD_CHANGE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void change(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest request,
                       HttpServletResponse response) {
        passwordService.changePassword(UUID.fromString(jwt.getSubject()), request.currentPassword(),
                request.newPassword());
        sessionCookies.clear(response);
    }

    /** Parolasız hesap: hesabın doğrulanmış adresine tek kullanımlık kod gider (TOTP etkinse ikinci faktör gerekir). */
    @PostMapping(AuthApiPaths.PASSWORD_SET_REQUEST)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public VerificationPendingResponse requestSet(@AuthenticationPrincipal Jwt jwt,
                                                  @Valid @RequestBody RequestPasswordSetRequest request) {
        passwordSetService.requestCode(UUID.fromString(jwt.getSubject()), request.code(), request.recoveryCode());
        return VerificationPendingResponse.VERIFY_EMAIL;
    }

    /** Başarıda tüm oturumlar (bu cihaz dahil) kapanır; cookie'ler silinir, yeni parolayla yeniden giriş gerekir. */
    @PostMapping(AuthApiPaths.PASSWORD_SET)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void set(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SetPasswordRequest request,
                    HttpServletResponse response) {
        passwordSetService.setPassword(UUID.fromString(jwt.getSubject()), request.emailCode(), request.newPassword());
        sessionCookies.clear(response);
    }

    @PostMapping(AuthApiPaths.PASSWORD_FORGOT)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public PasswordResetPendingResponse forgot(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordService.requestReset(request.email());
        return PasswordResetPendingResponse.CHECK_EMAIL;
    }

    /** Oturum açmaz ve 2FA'ya dokunmaz; yetki kaynağı gövdedeki tek kullanımlık token'dır. */
    @PostMapping(AuthApiPaths.PASSWORD_RESET)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@Valid @RequestBody ResetPasswordRequest request) {
        passwordService.resetPassword(request.token(), request.newPassword());
    }
}

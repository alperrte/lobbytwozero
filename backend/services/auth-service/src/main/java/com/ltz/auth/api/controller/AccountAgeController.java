package com.ltz.auth.api.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.auth.api.dto.request.AgeVerificationRequest;
import com.ltz.auth.application.service.AdultVerificationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Yaş doğrulaması (18+). Hesap kimliği yalnız token'ın {@code sub} değerinden gelir; gövdeden hesap kimliği alınmaz.
 * Yeni {@code adult_verified} iddiası mevcut access token'a eklenmez: istemci başarıdan sonra oturumu yenilemelidir
 * ({@code POST /session/refresh}); iddia en geç bir erişim token ömrü (≈60 sn) sonra token'a yansır. Geri almada eski
 * token aynı süre boyunca yetkili kalır.
 */
@RestController
@RequiredArgsConstructor
public class AccountAgeController {

    private final AdultVerificationService adultVerificationService;

    /** Yaşı beyan eder. 18 altı: 403 {@code AGE_NOT_ELIGIBLE}; deneme sınırı aşılırsa 429; zaten doğrulanmışsa 204. */
    @PostMapping(AuthApiPaths.ACCOUNT_AGE_VERIFICATION)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verify(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AgeVerificationRequest request) {
        adultVerificationService.verify(UUID.fromString(jwt.getSubject()), request.birthDate());
    }

    /** Doğrulamayı geri alır; doğrulama yoksa da 204 (idempotent). */
    @DeleteMapping(AuthApiPaths.ACCOUNT_AGE_VERIFICATION)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@AuthenticationPrincipal Jwt jwt) {
        adultVerificationService.revoke(UUID.fromString(jwt.getSubject()));
    }
}

package com.ltz.auth.api.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.auth.api.dto.request.TotpActivateRequest;
import com.ltz.auth.api.dto.request.TotpDisableRequest;
import com.ltz.auth.api.dto.response.RecoveryCodesResponse;
import com.ltz.auth.api.dto.response.TotpSetupResponse;
import com.ltz.auth.application.service.TotpSetup;
import com.ltz.auth.application.service.TwoFactorService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Kimliği doğrulanmış hesabın kendi TOTP yönetimi; hesap kimliği yalnız token'dan alınır. */
@RestController
@RequiredArgsConstructor
public class TwoFactorController {

    private final TwoFactorService twoFactorService;

    @PostMapping(AuthApiPaths.TOTP_SETUP)
    public TotpSetupResponse setup(@AuthenticationPrincipal Jwt jwt) {
        TotpSetup setup = twoFactorService.startSetup(accountId(jwt));
        return new TotpSetupResponse(setup.secret(), setup.otpauthUri());
    }

    @PostMapping(AuthApiPaths.TOTP_ACTIVATE)
    public RecoveryCodesResponse activate(@AuthenticationPrincipal Jwt jwt,
                                          @Valid @RequestBody TotpActivateRequest request) {
        return new RecoveryCodesResponse(twoFactorService.activate(accountId(jwt), request.code()));
    }

    @PostMapping(AuthApiPaths.TOTP_DISABLE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TotpDisableRequest request) {
        twoFactorService.disable(accountId(jwt), request.password(), request.code(), request.recoveryCode());
    }

    private static UUID accountId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}

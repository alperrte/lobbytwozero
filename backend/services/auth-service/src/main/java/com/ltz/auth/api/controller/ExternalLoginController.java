package com.ltz.auth.api.controller;

import java.net.URI;
import java.time.Clock;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import com.ltz.auth.api.dto.request.SteamRegistrationRequest;
import com.ltz.auth.api.dto.response.VerificationPendingResponse;
import com.ltz.auth.application.service.ExternalLoginResult;
import com.ltz.auth.application.service.GoogleLoginService;
import com.ltz.auth.application.service.StartedFlow;
import com.ltz.auth.application.service.SteamLoginService;
import com.ltz.auth.domain.exception.AuthException;
import com.ltz.auth.infrastructure.config.OAuthProperties;
import com.ltz.auth.infrastructure.config.OAuthProperties.Steam;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Tarayıcı gezinmesiyle çalışan harici giriş uçları. Sonuç, yapılandırmadaki SABİT frontend adresine URL fragment'i
 * ile iletilir (fragment sunucuya, log'lara ve Referer'a gitmez); token'lar yalnız HttpOnly cookie'dedir.
 * Fragment: status=authenticated | status=mfa_required&challenge=… | status=registration_required&token=… | error=KOD
 */
@RestController
@RequiredArgsConstructor
public class ExternalLoginController {

    private final GoogleLoginService googleLoginService;
    private final SteamLoginService steamLoginService;
    private final SessionCookies sessionCookies;
    private final OAuthProperties properties;
    private final Clock clock;

    @GetMapping(AuthApiPaths.GOOGLE_START)
    public ResponseEntity<Void> googleStart(HttpServletResponse response) {
        return redirectTo(googleLoginService.start(), response);
    }

    @GetMapping(AuthApiPaths.GOOGLE_CALLBACK)
    public ResponseEntity<Void> googleCallback(@RequestParam(required = false) String code,
                                               @RequestParam(required = false) String state,
                                               HttpServletRequest request, HttpServletResponse response) {
        return finish(() -> googleLoginService.callback(code, state, sessionCookies.oauthState(request).orElse(null)),
                response);
    }

    @GetMapping(AuthApiPaths.STEAM_START)
    public ResponseEntity<Void> steamStart(HttpServletResponse response) {
        return redirectTo(steamLoginService.start(), response);
    }

    @GetMapping(AuthApiPaths.STEAM_CALLBACK)
    public ResponseEntity<Void> steamCallback(HttpServletRequest request, HttpServletResponse response) {
        return finish(() -> steamLoginService.callback(request.getParameterMap(),
                sessionCookies.oauthState(request).orElse(null)), response);
    }

    /** Cookie okumaz ve oturum açmaz; yetki kaynağı gövdedeki tek kullanımlık kayıt token'ıdır. */
    @PostMapping(AuthApiPaths.STEAM_COMPLETE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public VerificationPendingResponse steamComplete(@Valid @RequestBody SteamRegistrationRequest request) {
        steamLoginService.completeRegistration(request.registrationToken(), request.email(), request.username());
        return VerificationPendingResponse.VERIFY_EMAIL;
    }

    private ResponseEntity<Void> redirectTo(StartedFlow flow, HttpServletResponse response) {
        sessionCookies.writeOAuthState(response, flow.state(), properties.flowTtl());
        return ResponseEntity.status(HttpStatus.FOUND).location(flow.redirectUri()).build();
    }

    private ResponseEntity<Void> finish(CallbackAction action, HttpServletResponse response) {
        try {
            ExternalLoginResult result = action.run();
            if (result.session() != null) {
                sessionCookies.write(response, result.session(), clock.instant());
            }
            return frontend(fragment(result));
        } catch (AuthException exception) {
            return frontend("error=" + exception.errorCode().name());
        } finally {
            // State tek kullanımlıktır; sonuç ne olursa olsun cookie silinir.
            sessionCookies.clearOAuthState(response);
        }
    }

    private static String fragment(ExternalLoginResult result) {
        return switch (result.outcome()) {
            case AUTHENTICATED -> "status=authenticated";
            case MFA_REQUIRED -> "status=mfa_required&challenge=" + result.challengeId();
            case REGISTRATION_REQUIRED -> "status=registration_required&token=" + result.registrationToken();
        };
    }

    private ResponseEntity<Void> frontend(String fragment) {
        URI location = UriComponentsBuilder.fromUri(properties.frontendRedirectUrl()).fragment(fragment).build().toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }

    @FunctionalInterface
    private interface CallbackAction {
        ExternalLoginResult run();
    }
}

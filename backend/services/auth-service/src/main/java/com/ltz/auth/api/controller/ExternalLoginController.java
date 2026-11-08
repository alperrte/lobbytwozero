package com.ltz.auth.api.controller;

import java.net.URI;
import java.time.Clock;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import com.ltz.auth.api.dto.request.ExternalRegistrationRequest;
import com.ltz.auth.api.dto.response.SessionStatusResponse;
import com.ltz.auth.application.service.ExternalLoginResult;
import com.ltz.auth.application.service.GoogleLoginService;
import com.ltz.auth.application.service.LoginResult;
import com.ltz.auth.application.service.StartedFlow;
import com.ltz.auth.application.service.SteamLoginService;
import com.ltz.auth.domain.exception.AuthException;
import com.ltz.auth.infrastructure.config.OAuthProperties;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Tarayıcı gezinmesiyle çalışan harici giriş uçları. Sonuç, yapılandırmadaki SABİT frontend adresine URL fragment'i
 * ile iletilir (fragment sunucuya, log'lara ve Referer'a gitmez); token'lar yalnız HttpOnly cookie'dedir.
 * Fragment: status=authenticated | status=mfa_required&challenge=… | status=mfa_enrollment_required&challenge=… |
 * status=registration_required&provider=google|steam&token=… | error=KOD
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

    /**
     * İlk Steam girişinde kullanıcı adını seçip hesabı tamamlar. Yetki kaynağı gövdedeki tek kullanımlık kayıt token'ıdır
     * (e-posta alınmaz; Steam e-posta vermez). Yanıt giriş yanıtıyla aynıdır: oturum cookie'leri, MFA gerekiyorsa challenge.
     */
    @PostMapping(AuthApiPaths.STEAM_COMPLETE)
    public SessionStatusResponse steamComplete(@Valid @RequestBody ExternalRegistrationRequest request,
                                               HttpServletResponse response) {
        return establish(steamLoginService.completeRegistration(request.registrationToken(), request.username()), response);
    }

    /** İlk Google girişinde kullanıcı adını seçip hesabı tamamlar (e-posta Google'dan, sunucuda kayıtlıdır). */
    @PostMapping(AuthApiPaths.GOOGLE_COMPLETE)
    public SessionStatusResponse googleComplete(@Valid @RequestBody ExternalRegistrationRequest request,
                                                HttpServletResponse response) {
        return establish(googleLoginService.completeRegistration(request.registrationToken(), request.username()), response);
    }

    private SessionStatusResponse establish(LoginResult result, HttpServletResponse response) {
        if (result.requiresEnrollment()) {
            return SessionStatusResponse.enrollmentRequired(result.challengeId(), result.challengeExpiresAt());
        }
        if (result.requiresSecondFactor()) {
            return SessionStatusResponse.mfaRequired(result.challengeId(), result.challengeExpiresAt());
        }
        sessionCookies.write(response, result.session(), clock.instant());
        return SessionStatusResponse.authenticated(result.session().accessTokenExpiresAt());
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
            case MFA_ENROLLMENT_REQUIRED -> "status=mfa_enrollment_required&challenge=" + result.challengeId();
            case REGISTRATION_REQUIRED -> "status=registration_required&provider=steam&token=" + result.token();
            case GOOGLE_REGISTRATION_REQUIRED -> "status=registration_required&provider=google&token=" + result.token();
            case EMAIL_VERIFICATION_REQUIRED -> "status=email_verification_required&proof=" + result.token();
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

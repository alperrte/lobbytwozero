package com.ltz.auth.api.controller;

import java.time.Clock;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.auth.api.dto.request.LoginRequest;
import com.ltz.auth.api.dto.request.TwoFactorEnrollmentCompleteRequest;
import com.ltz.auth.api.dto.request.TwoFactorEnrollmentStartRequest;
import com.ltz.auth.api.dto.request.TwoFactorLoginRequest;
import com.ltz.auth.api.dto.response.EnrollmentCompletedResponse;
import com.ltz.auth.api.dto.response.SessionStatusResponse;
import com.ltz.auth.api.dto.response.TotpSetupResponse;
import com.ltz.auth.application.service.EnrollmentResult;
import com.ltz.auth.application.service.IssuedSession;
import com.ltz.auth.application.service.LoginResult;
import com.ltz.auth.application.service.LoginService;
import com.ltz.auth.application.service.TotpSetup;
import com.ltz.auth.application.service.TwoFactorService;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class LoginController {

    private final LoginService loginService;
    private final TwoFactorService twoFactorService;
    private final SessionCookies sessionCookies;
    private final Clock clock;

    @PostMapping(AuthApiPaths.LOGIN)
    public SessionStatusResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        LoginResult result = loginService.login(request.identifier(), request.password());
        if (result.requiresEnrollment()) {
            return SessionStatusResponse.enrollmentRequired(result.challengeId(), result.challengeExpiresAt());
        }
        if (result.requiresSecondFactor()) {
            return SessionStatusResponse.mfaRequired(result.challengeId(), result.challengeExpiresAt());
        }
        return establish(result.session(), response);
    }

    /** Zorunlu 2FA kurulumu adım 1: kurulum challenge'ı ile TOTP gizli anahtarını üretir (oturum yok). */
    @PostMapping(AuthApiPaths.LOGIN_TWO_FACTOR_ENROLLMENT_START)
    public TotpSetupResponse startEnrollment(@Valid @RequestBody TwoFactorEnrollmentStartRequest request) {
        TotpSetup setup = twoFactorService.startEnrollment(request.challengeId());
        return new TotpSetupResponse(setup.secret(), setup.otpauthUri());
    }

    /** Zorunlu 2FA kurulumu adım 2: ilk kodla TOTP etkinleşir, oturum açılır, kurtarma kodları bir kez döner. */
    @PostMapping(AuthApiPaths.LOGIN_TWO_FACTOR_ENROLLMENT_COMPLETE)
    public EnrollmentCompletedResponse completeEnrollment(@Valid @RequestBody TwoFactorEnrollmentCompleteRequest request,
                                                          HttpServletResponse response) {
        EnrollmentResult result = twoFactorService.completeEnrollment(request.challengeId(), request.code());
        sessionCookies.write(response, result.session(), clock.instant());
        return new EnrollmentCompletedResponse(SessionStatusResponse.Status.AUTHENTICATED,
                result.session().accessTokenExpiresAt(), result.recoveryCodes());
    }

    @PostMapping(AuthApiPaths.LOGIN_TWO_FACTOR)
    public SessionStatusResponse completeTwoFactor(@Valid @RequestBody TwoFactorLoginRequest request,
                                                   HttpServletResponse response) {
        return establish(twoFactorService.completeLogin(request.challengeId(), request.code(), request.recoveryCode()),
                response);
    }

    private SessionStatusResponse establish(IssuedSession session, HttpServletResponse response) {
        sessionCookies.write(response, session, clock.instant());
        return SessionStatusResponse.authenticated(session.accessTokenExpiresAt());
    }
}

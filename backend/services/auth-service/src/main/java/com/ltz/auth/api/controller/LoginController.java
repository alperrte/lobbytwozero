package com.ltz.auth.api.controller;

import java.time.Clock;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.auth.api.dto.request.LoginRequest;
import com.ltz.auth.api.dto.request.TwoFactorLoginRequest;
import com.ltz.auth.api.dto.response.SessionStatusResponse;
import com.ltz.auth.application.service.IssuedSession;
import com.ltz.auth.application.service.LoginResult;
import com.ltz.auth.application.service.LoginService;
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
        if (result.requiresSecondFactor()) {
            return SessionStatusResponse.mfaRequired(result.challengeId(), result.challengeExpiresAt());
        }
        return establish(result.session(), response);
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

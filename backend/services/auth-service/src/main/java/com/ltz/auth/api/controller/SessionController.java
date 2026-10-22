package com.ltz.auth.api.controller;

import java.time.Clock;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.auth.api.dto.response.SessionStatusResponse;
import com.ltz.auth.application.service.IssuedSession;
import com.ltz.auth.application.service.SessionService;
import com.ltz.auth.domain.exception.AuthErrorCode;
import com.ltz.auth.domain.exception.AuthException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;
    private final SessionCookies sessionCookies;
    private final Clock clock;

    /** XSRF-TOKEN cookie'sini üretir; SPA değeri X-XSRF-TOKEN başlığında geri gönderir. */
    @GetMapping(AuthApiPaths.CSRF)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void csrf(CsrfToken csrfToken) {
        csrfToken.getToken();
    }

    @PostMapping(AuthApiPaths.SESSION_REFRESH)
    public SessionStatusResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        try {
            String refreshToken = sessionCookies.refreshToken(request)
                    .orElseThrow(() -> new AuthException(AuthErrorCode.SESSION_INVALID));
            IssuedSession session = sessionService.refresh(refreshToken);
            sessionCookies.write(response, session, clock.instant());
            return SessionStatusResponse.authenticated(session.accessTokenExpiresAt());
        } catch (AuthException exception) {
            if (exception.errorCode() == AuthErrorCode.SESSION_INVALID) {
                sessionCookies.clear(response);
            }
            throw exception;
        }
    }

    /** Bu cihazdaki oturumu kapatır; tekrar çağrılması güvenlidir (idempotent). */
    @PostMapping(AuthApiPaths.SESSION_LOGOUT)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        sessionCookies.refreshToken(request).ifPresent(sessionService::logout);
        sessionCookies.clear(response);
    }

    @PostMapping(AuthApiPaths.SESSION_LOGOUT_ALL)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logoutAll(@AuthenticationPrincipal Jwt jwt, HttpServletResponse response) {
        sessionService.logoutAll(UUID.fromString(jwt.getSubject()));
        sessionCookies.clear(response);
    }
}

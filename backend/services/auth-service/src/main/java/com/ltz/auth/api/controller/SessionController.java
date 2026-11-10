package com.ltz.auth.api.controller;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.auth.api.dto.response.CurrentAccountResponse;
import com.ltz.auth.api.dto.response.SessionStatusResponse;
import com.ltz.auth.application.service.AccountProfile;
import com.ltz.auth.application.service.AccountTierService;
import com.ltz.auth.application.service.IssuedSession;
import com.ltz.auth.application.service.SessionService;
import com.ltz.auth.domain.exception.AuthErrorCode;
import com.ltz.auth.domain.exception.AuthException;
import com.ltz.auth.infrastructure.persistence.repository.AccountRepository;
import com.ltz.auth.infrastructure.security.TokenClaims;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;
    private final AccountRepository accountRepository;
    private final AccountTierService tierService;
    private final SessionCookies sessionCookies;
    private final Clock clock;

    /** XSRF-TOKEN cookie'sini üretir; SPA değeri X-XSRF-TOKEN başlığında geri gönderir. */
    @GetMapping(AuthApiPaths.CSRF)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void csrf(CsrfToken csrfToken) {
        csrfToken.getToken();
    }

    /**
     * Girişli kullanıcının kim olduğunu söyler (SPA oturum yoklaması). Sayfa yenilenince çerez JS tarafından okunamadığı
     * için arayüz kimlik ve rolü buradan öğrenir. 401 = misafir ya da oturum geçersiz (yenileme denenir). Oturumun
     * canlılığı (iptal, askı, parola sonrası) diğer kimlikli uçlarla aynı denetimden geçer; yanıt önbelleğe alınmaz.
     */
    @GetMapping(AuthApiPaths.ME)
    public ResponseEntity<CurrentAccountResponse> me(@AuthenticationPrincipal Jwt jwt) {
        UUID accountId = UUID.fromString(jwt.getSubject());
        AccountProfile profile = tierService.profileOf(accountRepository.findById(accountId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.SESSION_INVALID)));
        CurrentAccountResponse body = new CurrentAccountResponse(accountId,
                claimList(jwt, TokenClaims.ROLES), claimList(jwt, TokenClaims.AUTH_METHODS), jwt.getExpiresAt(),
                profile.passwordSet(), profile.providers(), profile.totpEnabled(), profile.registrationMethod(),
                profile.tier(), profile.tier().isTrusted());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    private static List<String> claimList(Jwt jwt, String name) {
        List<String> values = jwt.getClaimAsStringList(name);
        return values == null ? List.of() : List.copyOf(values);
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

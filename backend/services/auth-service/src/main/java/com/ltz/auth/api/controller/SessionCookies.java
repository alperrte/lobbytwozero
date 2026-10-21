package com.ltz.auth.api.controller;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

import com.ltz.auth.application.service.IssuedSession;
import com.ltz.auth.infrastructure.config.SessionProperties;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * D2: token'lar yalnız HttpOnly cookie'de taşınır (JavaScript okuyamaz).
 * <ul>
 *   <li>access: SameSite=Lax, Path=/ (OAuth dönüşü gibi üst düzey gezinmelerde de gönderilir).</li>
 *   <li>refresh: SameSite=Strict, Path=/api/v1/auth/session (yalnız yenileme/çıkış uçlarına gider).</li>
 * </ul>
 * Secure açıkken __Host-/__Secure- önekleri tarayıcıya ek kısıt uygulatır. prod profilinde Secure kapatılamaz.
 */
@Component
public class SessionCookies {

    static final String ACCESS_COOKIE = "ltz_access";
    static final String REFRESH_COOKIE = "ltz_refresh";
    static final String CSRF_COOKIE = "XSRF-TOKEN";
    static final String OAUTH_COOKIE = "ltz_oauth";
    private static final String HOST_PREFIX = "__Host-";
    private static final String SECURE_PREFIX = "__Secure-";
    private static final String ROOT_PATH = "/";
    private static final String SAME_SITE_LAX = "Lax";
    private static final String SAME_SITE_STRICT = "Strict";
    private static final String PROD_PROFILE = "prod";

    private final boolean secure;
    private final String accessCookieName;
    private final String refreshCookieName;
    private final String csrfCookieName;
    private final String oauthCookieName;

    public SessionCookies(SessionProperties properties, Environment environment) {
        if (!properties.secureCookies() && environment.matchesProfiles(PROD_PROFILE)) {
            throw new IllegalStateException("ltz.auth.session.secure-cookies must be true in the 'prod' profile");
        }
        this.secure = properties.secureCookies();
        this.accessCookieName = secure ? HOST_PREFIX + ACCESS_COOKIE : ACCESS_COOKIE;
        this.refreshCookieName = secure ? SECURE_PREFIX + REFRESH_COOKIE : REFRESH_COOKIE;
        this.csrfCookieName = secure ? HOST_PREFIX + CSRF_COOKIE : CSRF_COOKIE;
        this.oauthCookieName = secure ? HOST_PREFIX + OAUTH_COOKIE : OAUTH_COOKIE;
    }

    public String accessCookieName() {
        return accessCookieName;
    }

    public String csrfCookieName() {
        return csrfCookieName;
    }

    public void write(HttpServletResponse response, IssuedSession session, Instant now) {
        add(response, accessCookie(session.accessToken(), Duration.between(now, session.accessTokenExpiresAt())));
        add(response, refreshCookie(session.refreshToken(), Duration.between(now, session.refreshTokenExpiresAt())));
    }

    public void clear(HttpServletResponse response) {
        add(response, accessCookie("", Duration.ZERO));
        add(response, refreshCookie("", Duration.ZERO));
    }

    /**
     * Harici giriş akışının state cookie'si: callback'i başlatan tarayıcıya bağlar (login CSRF koruması).
     * Sağlayıcıdan dönüş üst düzey GET gezinmesi olduğundan SameSite=Lax gerekir; Path=/ __Host- önekinin şartıdır.
     */
    public void writeOAuthState(HttpServletResponse response, String state, Duration ttl) {
        add(response, oauthStateCookie(state, ttl));
    }

    public void clearOAuthState(HttpServletResponse response) {
        add(response, oauthStateCookie("", Duration.ZERO));
    }

    public Optional<String> oauthState(HttpServletRequest request) {
        return Optional.ofNullable(WebUtils.getCookie(request, oauthCookieName))
                .map(Cookie::getValue)
                .filter(value -> !value.isBlank());
    }

    private ResponseCookie oauthStateCookie(String value, Duration maxAge) {
        return ResponseCookie.from(oauthCookieName, value)
                .httpOnly(true).secure(secure).sameSite(SAME_SITE_LAX).path(ROOT_PATH).maxAge(maxAge).build();
    }

    public Optional<String> refreshToken(HttpServletRequest request) {
        return Optional.ofNullable(WebUtils.getCookie(request, refreshCookieName))
                .map(Cookie::getValue)
                .filter(value -> !value.isBlank());
    }

    private ResponseCookie accessCookie(String value, Duration maxAge) {
        return ResponseCookie.from(accessCookieName, value)
                .httpOnly(true).secure(secure).sameSite(SAME_SITE_LAX).path(ROOT_PATH).maxAge(maxAge).build();
    }

    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(refreshCookieName, value)
                .httpOnly(true).secure(secure).sameSite(SAME_SITE_STRICT).path(AuthApiPaths.SESSION).maxAge(maxAge)
                .build();
    }

    private static void add(HttpServletResponse response, ResponseCookie cookie) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}

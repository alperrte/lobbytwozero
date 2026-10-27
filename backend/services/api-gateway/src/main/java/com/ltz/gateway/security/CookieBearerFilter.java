package com.ltz.gateway.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Tarayıcı oturumunu servislerin beklediği Authorization: Bearer başlığına çevirir (B3).
 * <p>
 * Auth erişim token'ını HttpOnly çerezde tutar; tarayıcı çerezi otomatik gönderir, başlığı göndermez. Servisler Bearer
 * isteklerini CSRF'den muaf tuttuğu için, çerezle gelen durum değiştiren isteklerde CSRF'yi (double-submit çerez/başlık
 * eşleşmesi + Origin kontrolü) burada doğrularız; doğrulanmayan istek servise ulaşmaz. Auth rotası çerezi kendisi işler
 * ve değiştirilmez. İstekte zaten Authorization başlığı varsa dokunulmaz (Bearer kullanan istemciler).
 */
@Component
@Order(40)
public class CookieBearerFilter extends OncePerRequestFilter {

    static final Set<String> ACCESS_COOKIES = Set.of("__Host-ltz_access", "ltz_access");
    static final Set<String> CSRF_COOKIES = Set.of("__Host-XSRF-TOKEN", "XSRF-TOKEN");
    static final String CSRF_HEADER = "X-XSRF-TOKEN";
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final Set<String> allowedOrigins;

    public CookieBearerFilter(GatewaySecurityProperties properties) {
        this.allowedOrigins = Set.copyOf(properties.cors().allowedOrigins());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = GatewayPaths.path(request);
        return !GatewayPaths.isApi(path) || GatewayPaths.isAuth(path) || "OPTIONS".equals(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getHeader("Authorization") != null) {
            chain.doFilter(request, response);
            return;
        }
        String accessToken = cookie(request, ACCESS_COOKIES);
        if (accessToken == null || accessToken.isBlank()) {
            chain.doFilter(request, response);
            return;
        }
        if (!SAFE_METHODS.contains(request.getMethod())) {
            String origin = request.getHeader("Origin");
            if (origin != null && !allowedOrigins.contains(origin)) {
                ProblemResponses.write(response, 403, "Forbidden", "ORIGIN_NOT_ALLOWED");
                return;
            }
            if (!csrfValid(request)) {
                ProblemResponses.write(response, 403, "Forbidden", "CSRF_TOKEN_INVALID");
                return;
            }
        }
        chain.doFilter(new HeaderOverrideRequest(request, Set.of()).add("Authorization", "Bearer " + accessToken),
                response);
    }

    private static boolean csrfValid(HttpServletRequest request) {
        String fromCookie = cookie(request, CSRF_COOKIES);
        String fromHeader = request.getHeader(CSRF_HEADER);
        if (fromCookie == null || fromCookie.isEmpty() || fromHeader == null || fromHeader.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(fromCookie.getBytes(StandardCharsets.UTF_8),
                fromHeader.getBytes(StandardCharsets.UTF_8));
    }

    private static String cookie(HttpServletRequest request, Set<String> names) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (names.contains(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}

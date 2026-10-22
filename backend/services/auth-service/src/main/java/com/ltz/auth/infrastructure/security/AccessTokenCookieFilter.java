package com.ltz.auth.infrastructure.security;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;

import org.springframework.http.HttpHeaders;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * HttpOnly access cookie'sini Bearer başlığına çevirir. CsrfFilter'dan SONRA çalışır (SecurityConfig);
 * bu sayede cookie tabanlı her korumalı istek CSRF denetimini geçmiş olur. İstek zaten Authorization
 * başlığı taşıyorsa veya uç public ise dokunulmaz.
 */
public class AccessTokenCookieFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final String accessCookieName;
    private final RequestMatcher publicEndpoints;

    public AccessTokenCookieFilter(String accessCookieName, RequestMatcher publicEndpoints) {
        this.accessCookieName = accessCookieName;
        this.publicEndpoints = publicEndpoints;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Cookie cookie = WebUtils.getCookie(request, accessCookieName);
        boolean translate = cookie != null && !cookie.getValue().isBlank()
                && request.getHeader(HttpHeaders.AUTHORIZATION) == null
                && !publicEndpoints.matches(request);
        chain.doFilter(translate ? new BearerRequest(request, BEARER_PREFIX + cookie.getValue()) : request, response);
    }

    private static final class BearerRequest extends HttpServletRequestWrapper {

        private final String authorization;

        BearerRequest(HttpServletRequest request, String authorization) {
            super(request);
            this.authorization = authorization;
        }

        @Override
        public String getHeader(String name) {
            return HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name) ? authorization : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name)
                    ? Collections.enumeration(Collections.singletonList(authorization))
                    : super.getHeaders(name);
        }
    }
}

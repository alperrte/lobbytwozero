package com.ltz.auth.infrastructure.security;

import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.util.matcher.RequestMatcher;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Access token'ı yalnız Authorization: Bearer başlığından okur; public uçlarda hiç okumaz.
 * <p>
 * Önemli: Spring Security, bu resolver'ın token bulduğu istekleri CSRF denetiminden otomatik muaf tutar.
 * Bu yüzden cookie burada OKUNMAZ; cookie, CSRF filtresinden SONRA {@link AccessTokenCookieFilter} ile
 * başlığa çevrilir. Böylece cookie ile kimlik doğrulayan her istek önce CSRF denetiminden geçer.
 */
public class AccessTokenResolver implements BearerTokenResolver {

    private final DefaultBearerTokenResolver headerResolver = new DefaultBearerTokenResolver();
    private final RequestMatcher publicEndpoints;

    public AccessTokenResolver(RequestMatcher publicEndpoints) {
        this.publicEndpoints = publicEndpoints;
    }

    @Override
    public String resolve(HttpServletRequest request) {
        return publicEndpoints.matches(request) ? null : headerResolver.resolve(request);
    }
}

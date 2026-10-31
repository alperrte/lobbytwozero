package com.ltz.review.infrastructure.security;

import com.ltz.review.application.service.AbuseQuotaService;
import com.ltz.review.domain.enums.AbuseAction;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Kimliği doğrulanmış kullanıcı isteklerinde kotayı tüketir. Güvenlik zinciri 401/403 verdiyse istek
 * buraya ulaşmaz; gövde doğrulaması ve iş kuralları kota tüketildikten sonra çalışır.
 */
public class AbuseQuotaInterceptor implements HandlerInterceptor {
    private final AbuseQuotaService quotas;
    private final AbuseAction action;
    private final Set<String> methods;

    public AbuseQuotaInterceptor(AbuseQuotaService quotas, AbuseAction action, Set<String> methods) {
        this.quotas = quotas;
        this.action = action;
        this.methods = methods;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!methods.contains(request.getMethod())) return true;
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token) {
            quotas.require(UUID.fromString(token.getToken().getSubject()), action);
        }
        return true;
    }
}

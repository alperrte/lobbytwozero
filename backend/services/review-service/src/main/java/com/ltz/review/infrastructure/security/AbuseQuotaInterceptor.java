package com.ltz.review.infrastructure.security;

import com.ltz.review.application.service.AbuseQuotaService;
import com.ltz.review.domain.enums.AbuseAction;
import com.ltz.review.domain.exception.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Kimliği doğrulanmış kullanıcı isteklerinde kotayı tüketir. Güvenlik zinciri 401/403 verdiyse istek
 * buraya ulaşmaz; gövde doğrulaması ve iş kuralları kota tüketildikten sonra çalışır.
 * Kota aşılırsa 429 ProblemDetail yanıtı burada tek kez yazılır ve {@code false} dönülür: istek denetçiye,
 * servise ve transaction'a hiç ulaşmaz.
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
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (!methods.contains(request.getMethod())
                || !(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token)) return true;
        try {
            quotas.require(UUID.fromString(token.getToken().getSubject()), action);
            return true;
        } catch (RateLimitExceededException limited) {
            reject(response, limited);
            return false;
        }
    }

    private static void reject(HttpServletResponse response, RateLimitExceededException limited) throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", Long.toString(limited.retryAfterSeconds()));
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getOutputStream().write(("{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,\"detail\":\""
                + limited.getMessage() + "\"}").getBytes(StandardCharsets.UTF_8));
    }
}

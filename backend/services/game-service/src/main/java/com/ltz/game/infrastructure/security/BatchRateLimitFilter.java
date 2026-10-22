package com.ltz.game.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Herkese açık toplu kart ucuna (POST /api/v1/game/games/batch) istemci başına istek sınırı uygular. Bağlantı havuzu küçüktür
 * (3); anonim ve sınırsız bir uç tüm servisi bloklayabilir. Sınır aşılınca 429 + Retry-After (ProblemDetail) döner.
 * Geçersiz gövdeli istekler de jeton harcar. Diğer uçlar bu filtreden etkilenmez.
 */
final class BatchRateLimitFilter extends OncePerRequestFilter {
    private final RequestMatcher publicBatch;
    private final TokenBucketRateLimiter limiter;
    private final ClientAddressResolver addresses;

    BatchRateLimitFilter(RequestMatcher publicBatch, TokenBucketRateLimiter limiter, ClientAddressResolver addresses) {
        this.publicBatch = publicBatch;
        this.limiter = limiter;
        this.addresses = addresses;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!publicBatch.matches(request)) {
            chain.doFilter(request, response);
            return;
        }
        var decision = limiter.tryAcquire(addresses.resolve(request));
        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(429);
        response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
        response.setContentType("application/problem+json");
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,"
                + "\"detail\":\"İstek sınırı aşıldı. Lütfen daha sonra tekrar deneyin.\",\"code\":\"RATE_LIMITED\"}");
    }
}

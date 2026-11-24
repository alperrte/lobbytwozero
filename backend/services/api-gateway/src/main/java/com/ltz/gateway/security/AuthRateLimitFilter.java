package com.ltz.gateway.security;

import java.io.IOException;
import java.time.Clock;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Auth uçlarına istemci IP'si başına dakikalık sınır (B1, ilk katman). Sayaç bu Gateway örneğinin belleğindedir;
 * çok örnekli kurulumda örnek başına uygulanır ve ortak bir limitleyici (Redis vb.) ile değiştirilmelidir.
 * JWKS uzaktan doğrulama için sınırlanmaz.
 */
@Component
@Order(30)
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final String JWKS_SUFFIX = "/.well-known/jwks.json";

    private final IpWindowCounter counter = new IpWindowCounter();
    private final int perMinute;
    private final Clock clock;

    public AuthRateLimitFilter(GatewaySecurityProperties properties, Clock clock) {
        this.perMinute = properties.authRateLimit().perMinute();
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (perMinute == 0 || "OPTIONS".equals(request.getMethod())) {
            return true;
        }
        String path = GatewayPaths.path(request);
        return !GatewayPaths.isAuth(path) || path.endsWith(JWKS_SUFFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long retryAfter = counter.hit(IpWindowCounter.clientKey(request), clock.millis(), perMinute);
        if (retryAfter > 0) {
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            ProblemResponses.write(response, 429, "Too Many Requests", "RATE_LIMITED");
            return;
        }
        chain.doFilter(request, response);
    }
}

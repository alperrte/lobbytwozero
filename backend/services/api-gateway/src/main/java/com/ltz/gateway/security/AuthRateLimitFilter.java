package com.ltz.gateway.security;

import java.io.IOException;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;

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

    private static final long WINDOW_MILLIS = 60_000L;
    private static final int PURGE_THRESHOLD = 50_000;
    private static final String JWKS_SUFFIX = "/.well-known/jwks.json";

    private record Window(long start, int count) {
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
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
        long now = clock.millis();
        long start = now - (now % WINDOW_MILLIS);
        if (windows.size() > PURGE_THRESHOLD) {
            windows.entrySet().removeIf(e -> e.getValue().start() != start);
        }
        Object attribute = request.getAttribute(ClientRequestSanitizerFilter.CLIENT_IP_ATTRIBUTE);
        String key = attribute == null ? request.getRemoteAddr() : attribute.toString();
        Window window = windows.merge(key, new Window(start, 1),
                (old, fresh) -> old.start() == start ? new Window(start, old.count() + 1) : fresh);
        if (window.count() > perMinute) {
            response.setHeader("Retry-After", String.valueOf(Math.max(1, (start + WINDOW_MILLIS - now + 999) / 1000)));
            ProblemResponses.write(response, 429, "Too Many Requests", "RATE_LIMITED");
            return;
        }
        chain.doFilter(request, response);
    }
}

package com.ltz.gateway.security;

import java.io.IOException;
import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Girişsiz okunabilen user uçlarına (herkese açık profil ve medya) istemci IP'si başına dakikalık sınır. Profil taraması
 * ve kullanıcı adı numaralandırmayı yavaşlatır. Varsayılan kapalıdır (0); {@code prod} profilinde
 * {@code application-prod.properties} ile açılır, bu yüzden geliştirmeyi etkilemez. Sayaç bu örneğin belleğindedir.
 */
@Component
@Order(31)
public class PublicRateLimitFilter extends OncePerRequestFilter {

    private final IpWindowCounter counter = new IpWindowCounter();
    private final int perMinute;
    private final Clock clock;

    public PublicRateLimitFilter(@Value("${ltz.gateway.public-rate-limit.per-minute:0}") int perMinute, Clock clock) {
        if (perMinute < 0 || perMinute > 100_000) {
            throw new IllegalArgumentException("ltz.gateway.public-rate-limit.per-minute must be between 0 and 100000");
        }
        this.perMinute = perMinute;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return perMinute == 0 || "OPTIONS".equals(request.getMethod())
                || !GatewayPaths.isPublicUser(GatewayPaths.path(request));
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

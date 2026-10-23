package com.ltz.gateway.security;

import java.io.IOException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Dışarıdan gelen isteğin kimlik taşıyıcı başlıklarını siler. Servisler kimliği yalnız doğrulanmış JWT'den alır
 * (SECURITY.md §4); istemci X-User-Id, X-Role gibi başlıklarla kimlik uyduramaz. Forwarded/X-Forwarded-* başlıkları
 * yalnız güvenilir proxy'den gelirse korunur; aksi halde silinir ve istemci IP'si doğrudan bağlantıdan alınır.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ClientRequestSanitizerFilter extends OncePerRequestFilter {

    static final String CLIENT_IP_ATTRIBUTE = "ltz.gateway.clientIp";

    private final Pattern trustedProxies;

    public ClientRequestSanitizerFilter(GatewaySecurityProperties properties) {
        this.trustedProxies = properties.trustedProxyPattern();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String remote = request.getRemoteAddr();
        boolean trusted = trustedProxies != null && remote != null && trustedProxies.matcher(remote).matches();

        Set<String> removed = new HashSet<>();
        var names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String lower = names.nextElement().toLowerCase(Locale.ROOT);
            if (isIdentityHeader(lower) || (!trusted && isForwardingHeader(lower))) {
                removed.add(lower);
            }
        }
        request.setAttribute(CLIENT_IP_ATTRIBUTE, clientIp(request, remote, trusted));
        chain.doFilter(removed.isEmpty() ? request : new HeaderOverrideRequest(request, removed), response);
    }

    static boolean isIdentityHeader(String lower) {
        return lower.startsWith("x-user-") || lower.startsWith("x-account-") || lower.startsWith("x-auth-")
                || lower.startsWith("x-internal-") || lower.equals("x-user") || lower.equals("x-userid")
                || lower.equals("x-role") || lower.equals("x-roles");
    }

    static boolean isForwardingHeader(String lower) {
        return lower.equals("forwarded") || lower.startsWith("x-forwarded-") || lower.equals("x-real-ip");
    }

    private static String clientIp(HttpServletRequest request, String remote, boolean trusted) {
        if (trusted) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String[] parts = forwarded.split(",");
                return parts[parts.length - 1].trim();
            }
        }
        return remote == null ? "unknown" : remote;
    }
}

package com.ltz.performance.infrastructure.security;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ltz.performance.auth")
public record PerformanceAuthProperties(URI serviceUrl, String issuer, String audience,
        Duration connectTimeout, Duration readTimeout) {
    public PerformanceAuthProperties {
        if (serviceUrl == null || serviceUrl.getHost() == null || serviceUrl.getRawUserInfo() != null
                || serviceUrl.getRawQuery() != null || serviceUrl.getRawFragment() != null
                || !("http".equals(serviceUrl.getScheme()) || "https".equals(serviceUrl.getScheme()))
                || !(serviceUrl.getPath().isEmpty() || "/".equals(serviceUrl.getPath()))) {
            throw new IllegalArgumentException("Auth service URL must be an HTTP(S) origin");
        }
        if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("Auth issuer and audience are required");
        }
        requireTimeout(connectTimeout);
        requireTimeout(readTimeout);
    }

    private static void requireTimeout(Duration timeout) {
        if (timeout == null || timeout.isNegative() || timeout.isZero()
                || timeout.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException("Auth HTTP timeout must be positive and at most 30 seconds");
        }
    }

    public URI jwksUri() { return serviceUrl.resolve("/api/v1/auth/.well-known/jwks.json"); }

    public URI devicesUri() { return serviceUrl.resolve("/api/v1/auth/devices"); }
}

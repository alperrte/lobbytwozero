package com.ltz.gateway.security;

import java.net.URI;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Gateway güvenlik katmanı ayarları.
 *
 * @param cors izin verilen tarayıcı kaynakları (tam eşleşme; "*" yasak, çünkü çerezle kimlik doğrulama kullanılır)
 * @param trustedProxies istemci IP'sini taşıyan başlıklara güvenilecek doğrudan bağlantı adresleri (regex); boşsa
 *        hiçbir proxy'ye güvenilmez ve gelen Forwarded/X-Forwarded-* başlıkları silinir
 * @param authRateLimit Auth uçları için istemci IP'si başına dakikalık istek sınırı (0 = kapalı)
 */
@ConfigurationProperties("ltz.gateway")
public record GatewaySecurityProperties(Cors cors, String trustedProxies, AuthRateLimit authRateLimit) {

    public GatewaySecurityProperties {
        if (cors == null) {
            throw new IllegalArgumentException("ltz.gateway.cors.allowed-origins is required");
        }
        if (trustedProxies != null && !trustedProxies.isBlank()) {
            Pattern.compile(trustedProxies);
        }
        authRateLimit = authRateLimit == null ? new AuthRateLimit(120) : authRateLimit;
    }

    public Pattern trustedProxyPattern() {
        return trustedProxies == null || trustedProxies.isBlank() ? null : Pattern.compile(trustedProxies);
    }

    public record Cors(List<String> allowedOrigins) {

        public Cors {
            if (allowedOrigins == null || allowedOrigins.isEmpty()) {
                throw new IllegalArgumentException("ltz.gateway.cors.allowed-origins must contain at least one origin");
            }
            allowedOrigins = allowedOrigins.stream().map(String::trim).filter(o -> !o.isEmpty()).toList();
            if (allowedOrigins.isEmpty()) {
                throw new IllegalArgumentException("ltz.gateway.cors.allowed-origins must contain at least one origin");
            }
            allowedOrigins.forEach(Cors::validate);
        }

        private static void validate(String origin) {
            URI uri;
            try {
                uri = URI.create(origin);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("ltz.gateway.cors.allowed-origins contains an invalid origin");
            }
            boolean schemeOk = "http".equals(uri.getScheme()) || "https".equals(uri.getScheme());
            boolean hostOnly = uri.getHost() != null && (uri.getPath() == null || uri.getPath().isEmpty())
                    && uri.getQuery() == null && uri.getFragment() == null && uri.getUserInfo() == null;
            if (!schemeOk || !hostOnly) {
                throw new IllegalArgumentException(
                        "ltz.gateway.cors.allowed-origins must be exact origins like https://app.example.com (no '*', path or credentials)");
            }
        }

        public boolean allHttps() {
            return allowedOrigins.stream().allMatch(o -> o.startsWith("https://"));
        }
    }

    public record AuthRateLimit(int perMinute) {

        public AuthRateLimit {
            if (perMinute < 0 || perMinute > 100_000) {
                throw new IllegalArgumentException("ltz.gateway.auth-rate-limit.per-minute must be between 0 and 100000");
            }
        }
    }
}

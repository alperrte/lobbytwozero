package com.ltz.performance.infrastructure.client;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Game ve Hardware servis adresleri mevcut ENV adlarından gelir (GAME_SERVICE_URL, HARDWARE_SERVICE_URL). */
@ConfigurationProperties("ltz.performance.clients")
public record PerformanceClientProperties(URI gameServiceUrl, URI hardwareServiceUrl,
        Duration connectTimeout, Duration readTimeout) {
    public PerformanceClientProperties {
        requireOrigin(gameServiceUrl);
        requireOrigin(hardwareServiceUrl);
        requireTimeout(connectTimeout);
        requireTimeout(readTimeout);
    }

    private static void requireOrigin(URI url) {
        if (url == null || url.getHost() == null || url.getRawUserInfo() != null
                || url.getRawQuery() != null || url.getRawFragment() != null
                || !("http".equals(url.getScheme()) || "https".equals(url.getScheme()))
                || !(url.getPath().isEmpty() || "/".equals(url.getPath()))) {
            throw new IllegalArgumentException("Service URL must be an HTTP(S) origin");
        }
    }

    private static void requireTimeout(Duration timeout) {
        if (timeout == null || timeout.isNegative() || timeout.isZero()
                || timeout.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException("HTTP timeout must be positive and at most 30 seconds");
        }
    }

    public URI gamesBatchUri() { return gameServiceUrl.resolve("/api/v1/game/games/batch"); }

    public URI hardwareProfilesBatchUri() { return hardwareServiceUrl.resolve("/api/v1/hardware/profiles/batch"); }
}

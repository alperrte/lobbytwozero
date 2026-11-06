package com.ltz.hardware.infrastructure.client;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Mevcut servis adresleri ve Hardware client credential'ı; gerçek ENV dosyası okunmaz. */
@ConfigurationProperties("ltz.hardware.clients")
public record HardwareClientProperties(URI userServiceUrl, URI performanceServiceUrl, String clientSecret) {
    public HardwareClientProperties {
        requireOrigin(userServiceUrl);
        requireOrigin(performanceServiceUrl);
        if (clientSecret == null) clientSecret = "";
        if (clientSecret.length() > 256) throw new IllegalArgumentException("Invalid Hardware service credential");
    }

    private static void requireOrigin(URI uri) {
        if (uri == null || uri.getHost() == null || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null || uri.getRawFragment() != null
                || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))) {
            throw new IllegalArgumentException("Service URL must be an HTTP(S) origin");
        }
    }

    public boolean configured() { return !clientSecret.isBlank(); }

    @Override public String toString() { return "HardwareClientProperties[clientSecret=***]"; }
}

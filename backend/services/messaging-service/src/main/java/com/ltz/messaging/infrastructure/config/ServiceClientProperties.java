package com.ltz.messaging.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * Giden servis çağrıları: Auth'tan servis token'ı almak ve social-service'e engel sormak. Adresler ortak ENV
 * değerlerinden gelir; gizli anahtar yalnız ortam değişkeninden ({@code AUTH_MESSAGING_SERVICE_CLIENT_SECRET}) gelir,
 * repoya yazılmaz. Gizli anahtar boşsa engel sorgulanamaz ve engel kontrolü gereken işlemler reddedilir.
 */
@Validated
@ConfigurationProperties("ltz.messaging.clients")
public record ServiceClientProperties(
        @NotBlank String authServiceUrl,
        @NotBlank String tokenPath,
        /** Auth'taki istemci kaydının adı. */
        @NotBlank String clientId,
        /** İstemci gizli anahtarı; boş olabilir (istemci kapalı, fail-closed). */
        String clientSecret,
        @NotBlank String socialServiceUrl,
        /** Social'in engel sorgusu iç yolu (docs/api.md → SOCIAL → Engel sorgusu). */
        @NotBlank String blocksCheckPath) {

    public boolean hasClientSecret() {
        return clientSecret != null && !clientSecret.isBlank();
    }

    @Override
    public String toString() {
        // Gizli anahtar kazara loglanmasın.
        return "ServiceClientProperties[authServiceUrl=%s, clientId=%s, socialServiceUrl=%s, blocksCheckPath=%s]"
                .formatted(authServiceUrl, clientId, socialServiceUrl, blocksCheckPath);
    }
}

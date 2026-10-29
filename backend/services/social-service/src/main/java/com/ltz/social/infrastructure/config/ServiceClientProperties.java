package com.ltz.social.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * Giden servis çağrıları: Auth'tan servis token'ı almak ve community-service'e olay iletmek. Adresler ortak ENV
 * değerlerinden gelir; gizli anahtar yalnız ortam değişkeninden ({@code AUTH_SOCIAL_SERVICE_CLIENT_SECRET}) gelir,
 * repoya yazılmaz. Gizli anahtar boşsa istemci kapalıdır ve giden olay denenmez.
 */
@Validated
@ConfigurationProperties("ltz.social.clients")
public record ServiceClientProperties(
        @NotBlank String authServiceUrl,
        @NotBlank String tokenPath,
        /** Auth'taki istemci kaydının adı. */
        @NotBlank String clientId,
        /** İstemci gizli anahtarı; boş olabilir (istemci kapalı). */
        String clientSecret,
        @NotBlank String communityServiceUrl) {

    @Override
    public String toString() {
        // Gizli anahtar kazara loglanmasın.
        return "ServiceClientProperties[authServiceUrl=" + authServiceUrl + ", clientId=" + clientId + "]";
    }
}

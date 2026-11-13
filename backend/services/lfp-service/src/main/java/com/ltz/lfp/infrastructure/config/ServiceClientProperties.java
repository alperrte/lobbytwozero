package com.ltz.lfp.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * Giden servis çağrıları: game-service'ten oyun kartı (kimliksiz), Auth'tan servis token'ı ve social-service'e engel
 * sorgusu. Adresler ortak ENV değerlerinden gelir; gizli anahtar yalnız ortam değişkeninden
 * ({@code AUTH_LFP_SERVICE_CLIENT_SECRET}) gelir, repoya yazılmaz. Gizli anahtar boşsa engel sorgulanamaz ve engel
 * kontrolü gereken işlemler fail-closed reddedilir. Zaman aşımı ve yönlendirme ayarları {@code spring.http.clients.*}
 * ile ortaktır.
 */
@Validated
@ConfigurationProperties("ltz.lfp.clients")
public record ServiceClientProperties(
        @NotBlank String gameServiceUrl,
        /** game-service'in herkese açık oyun kartı yolu; {@code {gameId}} yer tutucusu içerir. */
        @NotBlank String gameCardPath,
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
        return "ServiceClientProperties[gameServiceUrl=%s, authServiceUrl=%s, clientId=%s, socialServiceUrl=%s]"
                .formatted(gameServiceUrl, authServiceUrl, clientId, socialServiceUrl);
    }
}

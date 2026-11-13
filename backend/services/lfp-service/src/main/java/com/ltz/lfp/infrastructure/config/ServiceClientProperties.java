package com.ltz.lfp.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * Giden servis çağrıları. Adresler ortak ENV değerlerinden ({@code GAME_SERVICE_URL}) gelir; zaman aşımı ve
 * yönlendirme ayarları {@code spring.http.clients.*} ile ortaktır.
 */
@Validated
@ConfigurationProperties("ltz.lfp.clients")
public record ServiceClientProperties(
        @NotBlank String gameServiceUrl,
        /** game-service'in herkese açık oyun kartı yolu; {@code {gameId}} yer tutucusu içerir. */
        @NotBlank String gameCardPath) {
}

package com.ltz.mail.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * Mail Service'in Auth'a servis kimliğiyle (client credentials) bağlanması: platform kapısında alıcı adresini
 * {@code GET /internal/v1/accounts/{id}/contact} ile çözmek için ({@code account:contact:read}). Gizli anahtar boşsa
 * istemci kapalıdır ve platform kapısı istek kabul etmez (fail-closed). {@code toString()} anahtarı gizler.
 */
@Validated
@ConfigurationProperties("ltz.mail.auth-client")
public record AuthClientProperties(@NotBlank String baseUrl, @NotBlank String clientId, String clientSecret,
                                   @NotBlank String tokenPath, @NotBlank String contactPath) {

    public boolean configured() {
        return clientSecret != null && !clientSecret.isBlank();
    }

    @Override
    public String toString() {
        return "AuthClientProperties[baseUrl=" + baseUrl + ", clientId=" + clientId + ", clientSecret=***]";
    }
}

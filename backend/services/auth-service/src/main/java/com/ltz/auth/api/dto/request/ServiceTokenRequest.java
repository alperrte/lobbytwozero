package com.ltz.auth.api.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Servis istemcisi kimliği ve gizli anahtarı (client credentials). Gizli anahtar hiçbir yerde loglanmaz.
 * {@code audience}: token'ın geçeceği servis; boşsa {@code auth-service}. Yalnız istemciye tanımlı hedefler için token verilir.
 * {@code accountId}: yalnız hesaba bağlı kapsamlı hedefte (ör. {@code signals:read}) zorunlu, diğerlerinde verilemez.
 */
public record ServiceTokenRequest(
        @NotBlank @Pattern(regexp = "^[a-z0-9-]{1,32}$") String clientId,
        @NotBlank @Size(max = 256) String clientSecret,
        @Pattern(regexp = "^[a-z0-9-]{1,32}$") String audience,
        UUID accountId) {

    public ServiceTokenRequest(String clientId, String clientSecret) {
        this(clientId, clientSecret, null, null);
    }

    @Override
    public String toString() {
        return "ServiceTokenRequest[clientId=" + clientId + ", clientSecret=***, audience=" + audience
                + ", accountId=" + accountId + "]";
    }
}

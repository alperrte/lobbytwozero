package com.ltz.auth.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Servis istemcisi kimliği ve gizli anahtarı (client credentials). Gizli anahtar hiçbir yerde loglanmaz. */
public record ServiceTokenRequest(
        @NotBlank @Pattern(regexp = "^[a-z0-9-]{1,32}$") String clientId,
        @NotBlank @Size(max = 256) String clientSecret) {

    @Override
    public String toString() {
        return "ServiceTokenRequest[clientId=" + clientId + ", clientSecret=***]";
    }
}

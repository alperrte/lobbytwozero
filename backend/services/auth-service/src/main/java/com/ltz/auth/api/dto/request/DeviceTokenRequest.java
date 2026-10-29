package com.ltz.auth.api.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cihaz token isteği: cihaz kimliği ve bağlama sırasında bir kez verilen gizli anahtar. Anahtar hiçbir yerde loglanmaz. */
public record DeviceTokenRequest(
        @NotNull UUID deviceId,
        @NotBlank @Size(max = 128) String deviceSecret) {

    @Override
    public String toString() {
        return "DeviceTokenRequest[deviceId=" + deviceId + ", deviceSecret=***]";
    }
}

package com.ltz.auth.api.dto.request;

import java.util.UUID;

import com.ltz.auth.infrastructure.security.DeviceTokenTarget;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cihaz token isteği: cihaz kimliği ve bağlama sırasında bir kez verilen gizli anahtar. Anahtar hiçbir yerde loglanmaz.
 * {@code target}: token'ın gideceği servis ({@code hardware} veya {@code performance}); verilmezse {@code hardware}
 * (geriye dönük uyumlu). Tanımsız değer {@code 400}.
 */
public record DeviceTokenRequest(
        @NotNull UUID deviceId,
        @NotBlank @Size(max = 128) String deviceSecret,
        @Pattern(regexp = DeviceTokenTarget.REQUEST_PATTERN) String target) {

    public DeviceTokenRequest(UUID deviceId, String deviceSecret) {
        this(deviceId, deviceSecret, null);
    }

    @Override
    public String toString() {
        return "DeviceTokenRequest[deviceId=" + deviceId + ", deviceSecret=***, target=" + target + "]";
    }
}

package com.ltz.auth.api.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.ltz.auth.application.service.DeviceCredentialService.BoundDevice;

/**
 * Cihaz bağlama yanıtı. {@code deviceSecret} yalnız bu yanıtta bir kez görünür ve sonradan alınamaz; yanıt
 * önbelleğe alınmamalıdır (Cache-Control: no-store).
 */
public record BoundDeviceResponse(UUID deviceId, String deviceSecret, String label, Instant createdAt,
                                  Instant expiresAt) {

    public static BoundDeviceResponse from(BoundDevice bound) {
        return new BoundDeviceResponse(bound.device().getId(), bound.secret(), bound.device().getLabel(),
                bound.device().getCreatedAt(), bound.device().getExpiresAt());
    }

    @Override
    public String toString() {
        return "BoundDeviceResponse[deviceId=" + deviceId + ", deviceSecret=***, label=" + label + "]";
    }
}

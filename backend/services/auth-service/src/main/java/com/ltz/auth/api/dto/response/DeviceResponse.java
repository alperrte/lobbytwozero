package com.ltz.auth.api.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.ltz.auth.domain.entity.DeviceCredential;

/** Kullanıcının cihaz listesi öğesi. Gizli anahtar ve özeti hiçbir zaman dönmez. */
public record DeviceResponse(UUID deviceId, String label, Instant createdAt, Instant expiresAt, Instant lastUsedAt) {

    public static DeviceResponse from(DeviceCredential device) {
        return new DeviceResponse(device.getId(), device.getLabel(), device.getCreatedAt(), device.getExpiresAt(),
                device.getLastUsedAt());
    }
}

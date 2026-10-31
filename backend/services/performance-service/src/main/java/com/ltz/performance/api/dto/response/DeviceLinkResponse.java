package com.ltz.performance.api.dto.response;

import com.ltz.performance.domain.entity.DeviceLink;
import java.time.Instant;
import java.util.UUID;

/** Dış sözleşmede yalnız Auth cihaz kimliği ve durum görünür; iç kayıt kimliği yol parametresi olarak kullanılır. */
public record DeviceLinkResponse(UUID id, UUID deviceId, String label, String status, Instant linkedAt,
        Instant lastSyncAt, Instant disabledAt) {
    public static DeviceLinkResponse from(DeviceLink link) {
        return new DeviceLinkResponse(link.getId(), link.getAuthDeviceId(), link.getLabel(),
                link.getStatus().name(), link.getLinkedAt(), link.getLastSyncAt(), link.getDisabledAt());
    }
}

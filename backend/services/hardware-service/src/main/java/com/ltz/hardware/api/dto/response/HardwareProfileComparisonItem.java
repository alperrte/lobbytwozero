package com.ltz.hardware.api.dto.response;

import com.ltz.hardware.domain.enums.ComponentCategory;
import com.ltz.hardware.domain.enums.DeviceType;
import java.util.Map;
import java.util.UUID;

public record HardwareProfileComparisonItem(UUID profileId, DeviceType deviceType,
        Map<ComponentCategory, ComponentCountResponse> categories, Capacity ram, Capacity storage) {
    public record Capacity(long knownCapacityGb, long unitsWithoutCapacity) { }
}

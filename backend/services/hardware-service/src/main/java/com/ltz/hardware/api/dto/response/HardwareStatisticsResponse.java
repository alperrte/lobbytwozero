package com.ltz.hardware.api.dto.response;

import com.ltz.hardware.domain.enums.ComponentCategory;
import com.ltz.hardware.domain.enums.DeviceType;
import java.util.Map;

public record HardwareStatisticsResponse(long totalProfiles, Map<DeviceType, Long> profilesByDeviceType,
        long totalComponentEntries, long totalComponentUnits,
        Map<ComponentCategory, ComponentCountResponse> componentsByCategory,
        long recordedRamCapacityGb, long ramUnitsWithoutCapacity,
        long recordedStorageCapacityGb, long storageUnitsWithoutCapacity) { }

package com.ltz.hardware.api.dto.response;

import com.ltz.hardware.domain.enums.ComponentCategory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record HardwareScanPreviewResponse(long profileVersion, int schemaVersion, Instant capturedAt,
        int componentCount, boolean persisted, List<Component> components) {
    public record Component(UUID componentKey, ComponentCategory category, String modelName,
            String brandName, int quantity, SpecificationsResponse specifications) { }
}

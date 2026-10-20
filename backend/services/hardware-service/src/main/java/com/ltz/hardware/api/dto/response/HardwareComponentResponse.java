package com.ltz.hardware.api.dto.response;

import com.ltz.hardware.domain.enums.ComponentCategory;
import java.util.UUID;

public record HardwareComponentResponse(UUID id, ComponentCategory category, UUID productId, UUID brandId,
        String modelName, String brandName, int quantity, String source, long version,
        SpecificationsResponse specifications) { }

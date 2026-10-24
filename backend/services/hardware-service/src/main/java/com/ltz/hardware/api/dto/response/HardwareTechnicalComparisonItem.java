package com.ltz.hardware.api.dto.response;

import com.ltz.hardware.domain.enums.ComponentCategory;
import java.util.UUID;

public record HardwareTechnicalComparisonItem(UUID id, ComponentCategory category,
        String modelName, Integer quantity, SpecificationsResponse specifications) { }

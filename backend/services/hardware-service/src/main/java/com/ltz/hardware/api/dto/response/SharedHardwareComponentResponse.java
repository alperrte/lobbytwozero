package com.ltz.hardware.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ltz.hardware.domain.enums.ComponentCategory;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SharedHardwareComponentResponse(ComponentCategory category, String modelName,
        String brandName, Integer quantity, SpecificationsResponse specifications) { }

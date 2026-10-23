package com.ltz.hardware.api.dto.response;

import com.ltz.hardware.domain.enums.CatalogStatus;
import com.ltz.hardware.domain.enums.ComponentCategory;
import java.util.List;
import java.util.UUID;

public record HardwareProductResponse(UUID id, ComponentCategory category, String model,
        List<HardwareBrandResponse> brands, SpecificationsResponse specifications, CatalogStatus status, long version) { }

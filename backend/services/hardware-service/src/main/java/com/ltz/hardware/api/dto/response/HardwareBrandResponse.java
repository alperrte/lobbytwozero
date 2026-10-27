package com.ltz.hardware.api.dto.response;

import com.ltz.hardware.domain.enums.CatalogStatus;
import java.util.UUID;

public record HardwareBrandResponse(UUID id, String name, CatalogStatus status, long version) { }

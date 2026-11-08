package com.ltz.hardware.domain.entity;

import com.ltz.hardware.domain.entity.HardwareCatalogContent.Value;
import com.ltz.hardware.domain.enums.ComponentCategory;
import java.util.Map;

/** Only allowlisted, validated technical content reaches persistence or reports. */
public record PcPartProduct(ComponentCategory category, String name, String model, String brand,
        String identity, String contentHash, HardwareSpecifications specifications, Map<String, Value> technical) {
}

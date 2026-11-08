package com.ltz.hardware.api.dto.response;

import com.ltz.hardware.domain.enums.ComponentCategory;
import java.util.List;
import java.util.Map;

/** Distinct, recorded filter values for one category; {@code facets} keys are catalog filter parameter names. */
public record HardwareCatalogFacetsResponse(ComponentCategory category, Map<String, List<Option>> facets) {
    /** {@code value} is what the filter parameter expects; {@code label} is display text. */
    public record Option(String value, String label, long count) { }
}

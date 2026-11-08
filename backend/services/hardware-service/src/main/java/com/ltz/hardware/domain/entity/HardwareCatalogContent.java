package com.ltz.hardware.domain.entity;

import com.ltz.hardware.domain.enums.ComponentCategory;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Validated catalog content, never a raw provider envelope or user review. */
public record HardwareCatalogContent(String externalId, ComponentCategory category, String model, String brand,
        String mpn, List<String> gtins, String locale, Instant updatedAt, HardwareSpecifications specifications,
        Map<String, Value> typedSpecs, List<Group> featureGroups, List<Asset> assets, String contentHash) {
    public record Value(BigDecimal number, String text, String unit, String featureId) { }
    public record Feature(String id, String name, String value, String unit) { }
    public record Group(String name, List<Feature> features) { }
    public record Asset(String id, URI download, String alt, boolean primary) {
        @Override public String toString() { return "Asset[id=" + id + ", download=REDACTED]"; }
    }
}

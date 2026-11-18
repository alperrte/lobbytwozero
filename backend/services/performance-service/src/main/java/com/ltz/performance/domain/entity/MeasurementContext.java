package com.ltz.performance.domain.entity;

import java.util.Set;

/** Owner tarafından doğrulanan profil sürümü; cihazın v3 zarfında aynı bağlamı açıkça bildirmesi gerekir. */
public record MeasurementContext(long hardwareProfileVersion, String resolution, String graphicsPreset, String renderingMode) {
    public MeasurementContext {
        if (hardwareProfileVersion < 0 || !validResolution(resolution)
                || !Set.of("LOW", "MEDIUM", "HIGH", "ULTRA").contains(graphicsPreset == null ? "" : graphicsPreset)
                || !"NATIVE_RASTER".equals(renderingMode)) throw new IllegalArgumentException("Invalid measurement context");
    }

    private static boolean validResolution(String value) {
        if (value == null || !value.matches("[1-9][0-9]{2,4}x[1-9][0-9]{2,4}")) return false;
        String[] dimensions = value.split("x");
        return Integer.parseInt(dimensions[0]) <= 16384 && Integer.parseInt(dimensions[1]) <= 16384;
    }
}

package com.ltz.performance.api.dto.request;

import com.ltz.performance.domain.entity.MeasurementContext;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record MeasurementContextRequest(@NotNull @Min(0) Long hardwareProfileVersion,
        @NotBlank @Pattern(regexp = "[1-9]\\d{2,4}x[1-9]\\d{2,4}") String resolution,
        @NotBlank @Pattern(regexp = "LOW|MEDIUM|HIGH|ULTRA") String graphicsPreset,
        @NotBlank @Pattern(regexp = "NATIVE_RASTER") String renderingMode) {
    public MeasurementContext toDomain() {
        if (hardwareProfileVersion == null) throw new IllegalArgumentException("Profile version required");
        return new MeasurementContext(hardwareProfileVersion, resolution, graphicsPreset, renderingMode);
    }
}

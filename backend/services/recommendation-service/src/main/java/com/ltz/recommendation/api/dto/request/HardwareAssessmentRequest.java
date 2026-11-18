package com.ltz.recommendation.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record HardwareAssessmentRequest(@NotNull UUID hardwareProfileId, @NotNull UUID gameId,
        @NotBlank @Pattern(regexp = "[a-z0-9-]{1,32}") String platform,
        @Pattern(regexp = "[1-9]\\d{2,4}x[1-9]\\d{2,4}") String resolution,
        @Pattern(regexp = "LOW|MEDIUM|HIGH|ULTRA") String graphicsPreset,
        @Pattern(regexp = "NATIVE_RASTER") String renderingMode) {
    public HardwareAssessmentRequest(UUID hardwareProfileId, UUID gameId, String platform, String resolution, String graphicsPreset) {
        this(hardwareProfileId, gameId, platform, resolution, graphicsPreset, null);
    }
}

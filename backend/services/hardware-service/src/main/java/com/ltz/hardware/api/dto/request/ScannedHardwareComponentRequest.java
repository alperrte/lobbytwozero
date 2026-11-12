package com.ltz.hardware.api.dto.request;

import com.ltz.hardware.domain.enums.ComponentCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ScannedHardwareComponentRequest(@NotNull UUID componentKey,
        @NotNull ComponentCategory category, @NotBlank @Size(max = 160) String modelName,
        @Size(min = 1, max = 100) String brandName, @NotNull @Min(1) @Max(128) Integer quantity,
        @Valid SpecificationsRequest specifications) { }

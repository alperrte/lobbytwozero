package com.ltz.hardware.api.dto.request;

import com.ltz.hardware.domain.enums.ComponentCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;

public record ScannedHardwareComponentRequest(@NotNull UUID componentKey,
        @NotNull ComponentCategory category, @NotBlank @Size(max = 160) String modelName,
        @Size(min = 1, max = 100) String brandName, @NotNull @Min(1) @Max(128) Integer quantity,
        @Valid SpecificationsRequest specifications) { }

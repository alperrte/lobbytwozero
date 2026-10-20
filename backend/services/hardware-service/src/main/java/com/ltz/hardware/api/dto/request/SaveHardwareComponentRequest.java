package com.ltz.hardware.api.dto.request;

import com.ltz.hardware.domain.enums.ComponentCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;

public record SaveHardwareComponentRequest(
        @NotNull @PositiveOrZero Long profileVersion,
        @NotNull ComponentCategory category,
        UUID productId, UUID brandId,
        @Size(max = 160) @Pattern(regexp = "[^\\p{Cntrl}<>]+") String modelName,
        @Size(max = 100) @Pattern(regexp = "[^\\p{Cntrl}<>]+") String brandName,
        @NotNull @Min(1) @Max(128) Integer quantity,
        @Valid SpecificationsRequest specifications) { }

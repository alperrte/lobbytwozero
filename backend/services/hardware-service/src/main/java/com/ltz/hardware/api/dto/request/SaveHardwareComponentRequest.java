package com.ltz.hardware.api.dto.request;

import com.ltz.hardware.domain.enums.ComponentCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SaveHardwareComponentRequest(
        @NotNull @PositiveOrZero Long profileVersion,
        @NotNull ComponentCategory category,
        UUID productId, UUID brandId,
        @Size(max = 160) @Pattern(regexp = "[^\\p{Cntrl}<>]+") String modelName,
        @Size(max = 100) @Pattern(regexp = "[^\\p{Cntrl}<>]+") String brandName,
        @NotNull @Min(1) @Max(128) Integer quantity,
        @Valid SpecificationsRequest specifications) { }

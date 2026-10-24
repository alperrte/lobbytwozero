package com.ltz.hardware.api.dto.request;

import jakarta.validation.constraints.*;

public record UpdateHardwareReviewRequest(@NotNull @PositiveOrZero Long reviewVersion,
        @NotBlank @Size(max = 5000) String body, @Min(1) @Max(5) Integer rating) { }

package com.ltz.hardware.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdateHardwareReviewRequest(@NotNull @PositiveOrZero Long reviewVersion,
        @NotBlank @Size(max = 5000) String body, @HalfStepRating BigDecimal rating) { }

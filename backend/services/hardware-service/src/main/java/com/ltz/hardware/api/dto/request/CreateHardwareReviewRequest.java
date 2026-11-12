package com.ltz.hardware.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateHardwareReviewRequest(@NotBlank @Size(max = 5000) String body, @HalfStepRating BigDecimal rating) { }

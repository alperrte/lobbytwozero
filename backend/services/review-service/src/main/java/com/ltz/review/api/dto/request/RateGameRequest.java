package com.ltz.review.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RateGameRequest(@NotNull @Min(1) @Max(10) Integer rating) {}

package com.ltz.review.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateReviewRequest(@NotNull @Min(0) Long version, @NotBlank @Size(max = 5000) String body) {}

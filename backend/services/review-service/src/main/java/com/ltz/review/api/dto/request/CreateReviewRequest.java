package com.ltz.review.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateReviewRequest(@NotBlank @Size(max = 5000) String body) {}

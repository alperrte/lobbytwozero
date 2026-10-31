package com.ltz.review.api.dto.request;

import jakarta.validation.constraints.NotNull;

public record RecommendGameRequest(@NotNull Boolean recommended) {}

package com.ltz.recommendation.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record GenerateRecommendationsRequest(@Size(max = 64) String genre, @Size(max = 64) String platform,
        @Min(1) @Max(50) Integer size) { }

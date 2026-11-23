package com.ltz.recommendation.api.dto.request;

import com.ltz.recommendation.application.port.RecommendationHistory.FeedbackValue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RecommendationFeedbackRequest(@NotNull FeedbackValue feedback, @Min(0) long version) { }

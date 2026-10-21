package com.ltz.hardware.api.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record HardwareReviewPageResponse(List<HardwareReviewResponse> items, int page, int size,
        long totalElements, int totalPages, RatingSummary summary) {
    public record RatingSummary(long ratedReviews, BigDecimal averageRating) { }
}

package com.ltz.review.api.dto.response;

import java.util.List;

public record FeaturedReviewsResponse(List<FeaturedReviewResponse> items, int page, int size, long totalElements, long totalPages) {
    public FeaturedReviewsResponse { items = List.copyOf(items); }
}

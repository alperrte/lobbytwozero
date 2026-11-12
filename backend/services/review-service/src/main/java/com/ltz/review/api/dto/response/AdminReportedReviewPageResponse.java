package com.ltz.review.api.dto.response;

import java.util.List;

public record AdminReportedReviewPageResponse(List<AdminReportedReviewResponse> items, int page, int size, long totalElements, long totalPages) {
    public AdminReportedReviewPageResponse { items = List.copyOf(items); }
}

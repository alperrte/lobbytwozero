package com.ltz.hardware.api.dto.response;

import java.util.List;

public record HardwareOwnReviewPageResponse(List<HardwareReviewResponse> items, int page, int size,
        long totalElements, int totalPages) { }

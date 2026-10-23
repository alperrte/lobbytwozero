package com.ltz.hardware.api.dto.response;

import java.util.List;

public record HardwareReviewModerationDetailResponse(HardwareReviewResponse review,
        List<HardwareReviewReportResponse> reports, int page, int size, long totalReports, int totalPages) { }

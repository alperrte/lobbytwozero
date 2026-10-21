package com.ltz.hardware.application.mapper;

import com.ltz.hardware.api.dto.response.HardwareReviewReportResponse;
import com.ltz.hardware.domain.entity.HardwareReviewReport;
import java.util.Map;
import java.util.UUID;

public final class HardwareReviewReportMapper {
    private HardwareReviewReportMapper() { }

    public static HardwareReviewReportResponse fromStatuses(HardwareReviewReport report, Map<UUID, String> statuses) {
        return toResponse(report, report.getOutboxEventId() == null ? null : statuses.get(report.getOutboxEventId()));
    }

    public static HardwareReviewReportResponse toResponse(HardwareReviewReport report, String status) {
        return new HardwareReviewReportResponse(report.getId(), report.getReviewId(), report.getReason(), report.getDetails(),
                report.getCreatedAt(), status == null ? "NOT_AVAILABLE" : status,
                "PENDING".equals(status) ? "NOT_DELIVERED" : "NOT_VERIFIED");
    }
}

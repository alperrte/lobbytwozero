package com.ltz.performance.api.dto.response;

import com.ltz.performance.domain.entity.PerformanceReport;
import java.time.Instant;
import java.util.UUID;

public record ReportResponse(UUID id, UUID sessionId, String resolution, String graphicsPreset, Double avgFps,
        Double p1LowFps, Double maxCpuTempC, Double maxGpuTempC, Double avgCpuUsagePercent,
        Double avgGpuUsagePercent, String notes, String source, Instant createdAt, Instant updatedAt) {
    public static ReportResponse from(PerformanceReport report) {
        return new ReportResponse(report.getId(), report.getSessionId(), report.getResolution(),
                report.getGraphicsPreset().name(), report.getAvgFps(), report.getP1LowFps(), report.getMaxCpuTempC(),
                report.getMaxGpuTempC(), report.getAvgCpuUsagePercent(), report.getAvgGpuUsagePercent(),
                report.getNotes(), report.getSource().name(), report.getCreatedAt(), report.getUpdatedAt());
    }
}

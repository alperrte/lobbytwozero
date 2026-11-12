package com.ltz.hardware.api.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Owner profiline bağlı gerçek tamamlanmış oturum özeti; ham ölçüm veya oturum kimliği içermez. */
public record HardwarePerformanceSummaryResponse(UUID hardwareProfileId, long sessionCount, long sampleCount,
        Instant lastMeasuredAt, List<Metric> metrics) {
    public HardwarePerformanceSummaryResponse { metrics = List.copyOf(metrics); }

    public record Metric(String metric, String unit, long count, long sessionCount, double avg, double min,
            double max, Instant firstAt, Instant lastAt) { }
}

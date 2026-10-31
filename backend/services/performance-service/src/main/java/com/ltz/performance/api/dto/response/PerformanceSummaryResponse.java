package com.ltz.performance.api.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Hardware sözleşmesi: yalnız tamamlanmış oturumların özeti; oturum, oyun, cihaz kimliği veya başlık içermez. */
public record PerformanceSummaryResponse(UUID hardwareProfileId, long sessionCount, long sampleCount,
        Instant lastMeasuredAt, List<MetricStatsResponse> metrics) { }

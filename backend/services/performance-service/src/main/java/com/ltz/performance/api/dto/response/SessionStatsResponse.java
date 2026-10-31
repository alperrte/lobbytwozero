package com.ltz.performance.api.dto.response;

import java.util.List;
import java.util.UUID;

public record SessionStatsResponse(UUID sessionId, long sampleCount, List<MetricStatsResponse> metrics) { }

package com.ltz.performance.api.dto.response;

import java.time.Instant;

/** Bir metriğin gerçek örnekleri üzerindeki özeti; örnek yoksa metrik hiç listelenmez (0 ile doldurulmaz). */
public record MetricStatsResponse(String metric, String unit, long count, long sessionCount, double avg, double min,
        double max, Instant firstAt, Instant lastAt) { }

package com.ltz.performance.api.dto.response;

import java.util.List;

/** Filtreye uyan oturumların toplamı; ortalama örnek ağırlıklıdır (her örnek bir kez sayılır). */
public record AccountStatsResponse(long sessionCount, long sampleCount, List<MetricStatsResponse> metrics) { }

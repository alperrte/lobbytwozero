package com.ltz.performance.api.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * Grafik serisi. {@code resolution=RAW} ise noktalar ham örneklerdir; {@code BUCKET} ise örnekler
 * {@code bucketMillis} genişliğindeki kovalara ayrılıp ortalama/min/max ile özetlenmiştir ({@code downsampled=true}).
 * {@code totalSamples} filtreye uyan gerçek örnek sayısıdır.
 */
public record SeriesResponse(UUID sessionId, String metric, String unit, String resolution, boolean downsampled,
        Long bucketMillis, String aggregation, long totalSamples, List<SeriesPointResponse> points) { }

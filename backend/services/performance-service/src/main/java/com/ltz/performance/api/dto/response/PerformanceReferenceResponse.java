package com.ltz.performance.api.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * Recommendation sözleşmesi: bir oyun için güvenilir FPS referansı. Yetersiz veride {@code reliable=false} ve
 * FPS değerleri {@code null} döner; tüketici varsayım üretmemelidir.
 */
public record PerformanceReferenceResponse(UUID gameId, UUID hardwareProfileId, boolean reliable, long sessionCount,
        long fpsSampleCount, Double avgFps, Double minFps, Double maxFps, Instant lastMeasuredAt) { }

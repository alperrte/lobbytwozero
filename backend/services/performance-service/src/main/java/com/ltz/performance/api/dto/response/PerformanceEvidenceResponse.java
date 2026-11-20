package com.ltz.performance.api.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Bağlam eşleşmeli tarihsel dağılım; current FPS ölçümü veya attestation iddiası değildir. */
public record PerformanceEvidenceResponse(String contractVersion, UUID gameId, UUID hardwareProfileId,
        long hardwareProfileVersion, String resolution, String graphicsPreset, String renderingMode,
        String status, String source, String contextVerification, String method, int consideredSessionCount,
        long sessionCount, long observedSampleCount, long inlierSampleCount, long excludedSampleCount,
        Double lowerReferenceFps, Double upperReferenceFps, Instant firstMeasuredAt, Instant lastMeasuredAt,
        boolean windowLimited, String confidenceCeiling, List<String> limitations) {
    public PerformanceEvidenceResponse { limitations = List.copyOf(limitations); }
}

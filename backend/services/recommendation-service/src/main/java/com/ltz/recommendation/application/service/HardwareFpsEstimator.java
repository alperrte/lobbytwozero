package com.ltz.recommendation.application.service;

import com.ltz.recommendation.application.port.HardwareAssessmentSource;
import com.ltz.recommendation.application.port.HardwareAssessmentSource.EvidenceContext;
import com.ltz.recommendation.application.port.PersonalizationSource.Actor;
import com.ltz.recommendation.application.service.HardwareAssessmentService.Fps;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class HardwareFpsEstimator {
    private final HardwareAssessmentSource source;
    public HardwareFpsEstimator(HardwareAssessmentSource source) { this.source = source; }

    public Fps estimate(Actor actor, UUID profile, UUID game, long version, String resolution, String preset, String renderingMode) {
        if (resolution == null || preset == null || renderingMode == null) {
            var reference = source.reference(actor, profile, game);
            return new Fps("INSUFFICIENT_DATA", null, null, "NONE", reference.fpsSampleCount(), reference.sessionCount(),
                    reference.reliable(), reference.lastMeasuredAt(), resolution, preset,
                    reference.fpsSampleCount() == 0 ? "NO_FPS_SAMPLES" : "RESOLUTION_PRESET_PROVENANCE_AND_PROFILE_VERSION_UNBOUND", null);
        }
        var evidence = source.evidence(actor, new EvidenceContext(profile, game, version, resolution, preset, renderingMode));
        var provenance = new Provenance("fps-evidence-v1", "AUTHENTICATED_DEVICE", "OWNER_SNAPSHOT_DEVICE_DECLARATION",
                "SESSION_MEDIAN_P10_P90_IQR_1_5_V1", renderingMode, evidence.consideredSessionCount(), evidence.inlierSampleCount(),
                evidence.excludedSampleCount(), evidence.firstMeasuredAt(), evidence.windowLimited(), evidence.limitations());
        return new Fps(evidence.sufficient() ? "ESTIMATED" : "INSUFFICIENT_DATA",
                evidence.sufficient() ? Math.floor(evidence.lowerReferenceFps()) : null,
                evidence.sufficient() ? Math.ceil(evidence.upperReferenceFps()) : null,
                evidence.sufficient() ? "LOW" : "NONE", evidence.observedSampleCount(), evidence.sessionCount(),
                evidence.sufficient(), evidence.lastMeasuredAt(), resolution, preset,
                evidence.sufficient() ? "MATCHED_HISTORICAL_DEVICE_RANGE_NOT_CURRENT_MEASUREMENT"
                        : "MATCHED_CONTEXT_REQUIRES_3_RECENT_60_SAMPLE_60_SECOND_SESSIONS", provenance);
    }

    public record Provenance(String contractVersion, String source, String contextVerification, String method,
            String renderingMode, int consideredSessionCount, long inlierSampleCount, long excludedSampleCount,
            Instant firstMeasuredAt, boolean windowLimited, List<String> limitations) {
        public Provenance { limitations = List.copyOf(limitations); }
    }
}

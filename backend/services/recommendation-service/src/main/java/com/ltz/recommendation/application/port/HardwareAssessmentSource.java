package com.ltz.recommendation.application.port;

import com.ltz.recommendation.application.port.PersonalizationSource.Actor;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Yalnız doğrulanmış owner isteğinde, kaynak servislere ait teknik veriyi okur. */
public interface HardwareAssessmentSource {
    Profile profile(Actor actor, UUID profileId);
    List<Requirement> requirements(Actor actor, UUID gameId, String platform);
    Reference reference(Actor actor, UUID profileId, UUID gameId);
    Evidence evidence(Actor actor, EvidenceContext context);

    record Specs(Integer capacityGb, String memoryType, String socket) { }
    record Component(UUID id, String category, String modelName, int quantity, Specs specs) { }
    record Profile(UUID profileId, long profileVersion, List<Component> components) {
        public Profile { components = List.copyOf(components); }
    }
    record Requirement(String level, String cpu, String gpu, Integer ramMb, Integer storageMb, String os) { }
    record Reference(boolean reliable, long sessionCount, long fpsSampleCount, Double avgFps,
            Double minFps, Double maxFps, Instant lastMeasuredAt) { }
    record EvidenceContext(UUID profileId, UUID gameId, long profileVersion, String resolution, String graphicsPreset, String renderingMode) { }
    record Evidence(boolean sufficient, int consideredSessionCount, long sessionCount, long observedSampleCount,
            long inlierSampleCount, long excludedSampleCount, Double lowerReferenceFps, Double upperReferenceFps,
            Instant firstMeasuredAt, Instant lastMeasuredAt, boolean windowLimited, List<String> limitations) {
        public Evidence { limitations = List.copyOf(limitations); }
    }
}

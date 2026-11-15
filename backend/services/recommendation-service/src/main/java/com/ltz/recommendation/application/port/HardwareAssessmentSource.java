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

    record Specs(Integer capacityGb, String memoryType, String socket) { }
    record Component(UUID id, String category, String modelName, int quantity, Specs specs) { }
    record Profile(UUID profileId, long profileVersion, List<Component> components) {
        public Profile { components = List.copyOf(components); }
    }
    record Requirement(String level, String cpu, String gpu, Integer ramMb, Integer storageMb, String os) { }
    record Reference(boolean reliable, long sessionCount, long fpsSampleCount, Double avgFps,
            Double minFps, Double maxFps, Instant lastMeasuredAt) { }
}

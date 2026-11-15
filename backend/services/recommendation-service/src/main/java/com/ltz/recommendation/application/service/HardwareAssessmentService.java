package com.ltz.recommendation.application.service;

import com.ltz.recommendation.application.port.HardwareAssessmentSource;
import com.ltz.recommendation.application.port.HardwareAssessmentSource.Requirement;
import com.ltz.recommendation.application.port.PersonalizationSource.Actor;
import com.ltz.recommendation.application.service.HardwareRequirementEvaluator.Level;
import com.ltz.recommendation.application.service.HardwareRequirementEvaluator.Status;
import com.ltz.recommendation.application.service.HardwareRequirementEvaluator.Upgrade;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class HardwareAssessmentService {
    private final HardwareAssessmentSource source;
    private final PersonalizedRecommendationService consent;
    private final RecommendationRateLimiter limits;
    private final HardwareRequirementEvaluator requirements;
    private final ComponentCompatibilityEvaluator components;

    public HardwareAssessmentService(HardwareAssessmentSource source, PersonalizedRecommendationService consent,
            RecommendationRateLimiter limits, HardwareRequirementEvaluator requirements, ComponentCompatibilityEvaluator components) {
        this.source = source;
        this.consent = consent;
        this.limits = limits;
        this.requirements = requirements;
        this.components = components;
    }

    public Assessment assess(Actor actor, UUID profileId, UUID gameId, String platform, String resolution, String preset) {
        limits.acquire(actor.accountId(), true);
        consent.verifyConsent(actor);
        var profile = source.profile(actor, profileId);
        var game = source.requirements(actor, gameId, platform);
        Level minimum = requirements.evaluate(profile, level(game, "MINIMUM"), "MINIMUM");
        Level recommended = requirements.evaluate(profile, level(game, "RECOMMENDED"), "RECOMMENDED");
        var compatibility = components.evaluate(profile);
        var reference = source.reference(actor, profileId, gameId);
        var fps = new Fps("INSUFFICIENT_DATA", null, null, "NONE", reference.fpsSampleCount(), reference.sessionCount(),
                reference.reliable(), reference.lastMeasuredAt(), resolution, preset,
                reference.fpsSampleCount() == 0 ? "NO_FPS_SAMPLES" : "RESOLUTION_PRESET_PROVENANCE_AND_PROFILE_VERSION_UNBOUND");
        boolean suggestLow = minimum.computeStatus() == Status.MEETS && minimum.overallStatus() != Status.BELOW
                && !compatibility.status().equals("INCOMPATIBLE");
        var graphics = new Graphics(suggestLow ? "CONDITIONAL" : "INSUFFICIENT_DATA", suggestLow ? "LOW" : null,
                suggestLow ? "IF_AVAILABLE_START_LOW_AND_MEASURE_NO_FPS_PROMISE" : "MINIMUM_COMPUTE_OR_COMPONENTS_NOT_CONFIRMED");
        return new Assessment(profileId, profile.profileVersion(), gameId, platform, Instant.now(),
                minimum.overallStatus() == Status.BELOW ? "BELOW_MINIMUM" : "UNKNOWN", minimum, recommended,
                fps, graphics, requirements.upgrades(minimum, recommended), compatibility,
                List.of("DECLARED_TECHNICAL_DATA_ONLY", "OS_DRIVERS_AND_FREE_SPACE_NOT_VERIFIED",
                        "NO_CPU_GPU_MODEL_PERFORMANCE_ORDER", "NO_CATALOG_WIDE_HARDWARE_FILTER",
                        "NO_FPS_ESTIMATE_FROM_MIXED_AGGREGATE"));
    }

    private static Requirement level(List<Requirement> requirements, String level) {
        return requirements.stream().filter(requirement -> level.equals(requirement.level())).findFirst().orElse(null);
    }

    public record Fps(String status, Double estimatedLowerFps, Double estimatedUpperFps, String confidence,
            long observedSampleCount, long observedSessionCount, boolean producerSampleThresholdMet,
            Instant lastObservedAt, String requestedResolution, String requestedPreset, String reason) { }
    public record Graphics(String status, String suggestedPreset, String reason) { }
    public record Assessment(UUID hardwareProfileId, long hardwareProfileVersion, UUID gameId, String platform, Instant assessedAt,
            String suitability, Level minimum, Level recommended, Fps fps, Graphics graphics,
            List<Upgrade> upgrades, ComponentCompatibilityEvaluator.Result componentCompatibility, List<String> limitations) { }
}

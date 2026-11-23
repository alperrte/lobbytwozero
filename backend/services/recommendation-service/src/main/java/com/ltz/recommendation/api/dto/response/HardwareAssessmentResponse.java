package com.ltz.recommendation.api.dto.response;

import com.ltz.recommendation.application.service.ComponentCompatibilityEvaluator;
import com.ltz.recommendation.application.service.HardwareAssessmentService.Assessment;
import com.ltz.recommendation.application.service.HardwareAssessmentService.Fps;
import com.ltz.recommendation.application.service.HardwareAssessmentService.Graphics;
import com.ltz.recommendation.application.service.HardwareRequirementEvaluator.Level;
import com.ltz.recommendation.application.service.HardwareRequirementEvaluator.Upgrade;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record HardwareAssessmentResponse(UUID hardwareProfileId, long hardwareProfileVersion, UUID gameId,
        String platform, Instant assessedAt, String suitability, Level minimum, Level recommended, Fps fps,
        Graphics graphics, List<Upgrade> upgrades, ComponentCompatibilityEvaluator.Result componentCompatibility,
        List<String> limitations) {
    public static HardwareAssessmentResponse from(Assessment result) {
        return new HardwareAssessmentResponse(result.hardwareProfileId(), result.hardwareProfileVersion(), result.gameId(),
                result.platform(), result.assessedAt(), result.suitability(), result.minimum(), result.recommended(), result.fps(),
                result.graphics(), result.upgrades(), result.componentCompatibility(), result.limitations());
    }
}

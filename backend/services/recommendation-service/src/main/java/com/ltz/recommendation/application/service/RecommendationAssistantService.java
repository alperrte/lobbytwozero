package com.ltz.recommendation.application.service;

import com.ltz.recommendation.application.port.AssistantEngine;
import com.ltz.recommendation.application.port.AssistantEngine.Input;
import com.ltz.recommendation.application.port.AssistantEngine.Reply;
import com.ltz.recommendation.application.port.PersonalizationSource.Actor;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RecommendationAssistantService {
    private final AssistantEngine engine;
    private final HardwareAssessmentService hardware;
    private final PersonalizedRecommendationService consent;
    private final RecommendationRateLimiter limits;

    public RecommendationAssistantService(AssistantEngine engine, HardwareAssessmentService hardware,
            PersonalizedRecommendationService consent, RecommendationRateLimiter limits) {
        this.engine = engine;
        this.hardware = hardware;
        this.consent = consent;
        this.limits = limits;
    }

    public Reply advise(Actor actor, AssistantQuery query) {
        List<String> missing = missing(query);
        String minimum = "UNKNOWN";
        String recommended = "UNKNOWN";
        String compatibility = "UNKNOWN";
        if ("HARDWARE".equals(query.topic()) && missing.isEmpty()) {
            var result = hardware.assess(actor, query.hardwareProfileId(), query.gameId(), query.platform(), null, null);
            minimum = result.minimum().overallStatus().name();
            recommended = result.recommended().overallStatus().name();
            compatibility = result.componentCompatibility().status();
        } else {
            limits.acquire(actor.accountId(), true);
            consent.verifyConsent(actor);
        }
        return engine.advise(new Input(query.contractVersion(), query.topic(), query.intent(), query.locale(),
                query.genre() != null, missing, minimum, recommended, compatibility));
    }

    private static List<String> missing(AssistantQuery query) {
        List<String> fields = new ArrayList<>();
        if ("GAME".equals(query.topic()) && query.genre() == null) fields.add("GENRE");
        if ("HARDWARE".equals(query.topic())) {
            if (query.hardwareProfileId() == null) fields.add("HARDWARE_PROFILE_ID");
            if (query.gameId() == null) fields.add("GAME_ID");
            if (query.platform() == null) fields.add("PLATFORM");
        }
        return List.copyOf(fields);
    }
}

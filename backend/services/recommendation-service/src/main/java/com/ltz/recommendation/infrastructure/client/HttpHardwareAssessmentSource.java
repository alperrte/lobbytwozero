package com.ltz.recommendation.infrastructure.client;

import com.ltz.recommendation.application.port.HardwareAssessmentSource;
import com.ltz.recommendation.application.port.PersonalizationSource.Actor;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class HttpHardwareAssessmentSource implements HardwareAssessmentSource {
    private final RecommendationHttpClient http;
    private final RecommendationServiceTokenClient tokens;
    private final String hardwareUrl;
    private final String gameUrl;
    private final String performanceUrl;

    public HttpHardwareAssessmentSource(RecommendationHttpClient http, RecommendationServiceTokenClient tokens,
            @Value("${HARDWARE_SERVICE_URL:}") String hardwareUrl, @Value("${GAME_SERVICE_URL:}") String gameUrl,
            @Value("${PERFORMANCE_SERVICE_URL:}") String performanceUrl) {
        this.http = http;
        this.tokens = tokens;
        this.hardwareUrl = hardwareUrl;
        this.gameUrl = gameUrl;
        this.performanceUrl = performanceUrl;
    }

    @Override
    public Profile profile(Actor actor, UUID profileId) {
        var reply = http.postResource(RecommendationHttpClient.origin(hardwareUrl).resolve("/api/v1/hardware/profiles/batch"),
                Map.of("profiles", List.of(Map.of("profileId", profileId, "page", 0, "size", 100))), actor.bearer());
        return HardwareAssessmentResponseReader.profile(reply, profileId);
    }

    @Override
    public List<Requirement> requirements(Actor actor, UUID gameId, String platform) {
        var reply = http.getResource(RecommendationHttpClient.origin(gameUrl).resolve("/api/v1/game/" + gameId + "/metadata"), actor.bearer());
        return HardwareAssessmentResponseReader.requirements(reply, gameId, platform);
    }

    @Override
    public Reference reference(Actor actor, UUID profileId, UUID gameId) {
        var uri = UriComponentsBuilder.fromUri(RecommendationHttpClient.origin(performanceUrl))
                .path("/internal/v1/accounts/{account}/games/{game}/performance-reference")
                .queryParam("hardwareProfileId", profileId).buildAndExpand(actor.accountId(), gameId).toUri();
        return PerformanceReferenceResponseReader.read(http.getResource(uri, tokens.performanceToken(actor.accountId())), profileId, gameId);
    }

    @Override
    public Evidence evidence(Actor actor, EvidenceContext context) {
        var uri = UriComponentsBuilder.fromUri(RecommendationHttpClient.origin(performanceUrl))
                .path("/internal/v1/accounts/{account}/games/{game}/performance-evidence")
                .queryParam("hardwareProfileId", context.profileId()).queryParam("hardwareProfileVersion", context.profileVersion())
                .queryParam("resolution", context.resolution()).queryParam("graphicsPreset", context.graphicsPreset())
                .queryParam("renderingMode", context.renderingMode()).buildAndExpand(actor.accountId(), context.gameId()).toUri();
        return PerformanceEvidenceResponseReader.read(http.getResource(uri, tokens.performanceToken(actor.accountId())),
                context, java.time.Instant.now());
    }
}

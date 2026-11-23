package com.ltz.recommendation.infrastructure.client;

import static com.ltz.recommendation.infrastructure.client.AssessmentValues.count;
import static com.ltz.recommendation.infrastructure.client.AssessmentValues.fps;
import static com.ltz.recommendation.infrastructure.client.AssessmentValues.instant;
import static com.ltz.recommendation.infrastructure.client.AssessmentValues.invalid;
import static com.ltz.recommendation.infrastructure.client.AssessmentValues.uuid;

import com.ltz.recommendation.application.port.HardwareAssessmentSource.Reference;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

final class PerformanceReferenceResponseReader {
    private PerformanceReferenceResponseReader() { }

    static Reference read(JsonNode reply, UUID profileId, UUID gameId) {
        if (!gameId.equals(uuid(reply.path("gameId"))) || !profileId.equals(uuid(reply.path("hardwareProfileId")))
                || !reply.path("reliable").isBoolean()) throw invalid();
        long sessions = count(reply.path("sessionCount"));
        long samples = count(reply.path("fpsSampleCount"));
        if (sessions > samples) throw invalid();
        boolean reliable = reply.path("reliable").asBoolean();
        Double avg = fps(reply.path("avgFps"));
        Double min = fps(reply.path("minFps"));
        Double max = fps(reply.path("maxFps"));
        validateStatistics(reliable, samples, avg, min, max);
        return new Reference(reliable, sessions, samples, avg, min, max, instant(reply.path("lastMeasuredAt")));
    }

    private static void validateStatistics(boolean reliable, long samples, Double avg, Double min, Double max) {
        if (reliable && (samples < 60 || avg == null || min == null || max == null || min > avg || avg > max)) throw invalid();
    }
}

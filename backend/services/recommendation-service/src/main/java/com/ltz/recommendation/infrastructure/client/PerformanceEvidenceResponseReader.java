package com.ltz.recommendation.infrastructure.client;

import static com.ltz.recommendation.infrastructure.client.AssessmentValues.count;
import static com.ltz.recommendation.infrastructure.client.AssessmentValues.fps;
import static com.ltz.recommendation.infrastructure.client.AssessmentValues.instant;
import static com.ltz.recommendation.infrastructure.client.AssessmentValues.invalid;
import static com.ltz.recommendation.infrastructure.client.AssessmentValues.uuid;

import com.ltz.recommendation.application.port.HardwareAssessmentSource.Evidence;
import com.ltz.recommendation.application.port.HardwareAssessmentSource.EvidenceContext;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import tools.jackson.databind.JsonNode;

final class PerformanceEvidenceResponseReader {
    static final String METHOD = "SESSION_MEDIAN_P10_P90_IQR_1_5_V1";
    private static final Set<String> LIMITATIONS = Set.of("DEVICE_CONTEXT_NOT_ATTESTED", "GAME_BUILD_AND_BACKGROUND_LOAD_UNCONTROLLED",
            "HISTORICAL_RANGE_NOT_CURRENT_MEASUREMENT", "MAX_20_SESSIONS_600_SAMPLES_30_DAYS");
    private PerformanceEvidenceResponseReader() { }

    static Evidence read(JsonNode value, EvidenceContext context, Instant now) {
        binding(value, context);
        String status = value.path("status").asString();
        if (!Set.of("SUFFICIENT", "INSUFFICIENT_DATA").contains(status)) throw invalid();
        long considered = count(value.path("consideredSessionCount"));
        long sessions = count(value.path("sessionCount"));
        long observed = count(value.path("observedSampleCount"));
        long inliers = count(value.path("inlierSampleCount"));
        long excluded = count(value.path("excludedSampleCount"));
        counts(considered, sessions, observed, inliers, excluded);
        Instant first = instant(value.path("firstMeasuredAt"));
        Instant last = instant(value.path("lastMeasuredAt"));
        times(first, last, sessions, now);
        Double lower = fps(value.path("lowerReferenceFps"));
        Double upper = fps(value.path("upperReferenceFps"));
        boolean sufficient = status.equals("SUFFICIENT");
        range(sufficient, sessions, lower, upper);
        var limited = value.path("windowLimited");
        if (!limited.isBoolean() || limited.asBoolean() && considered != 20) throw invalid();
        return new Evidence(sufficient, (int) considered, sessions, observed, inliers, excluded, lower, upper,
                first, last, limited.asBoolean(), limitations(value.path("limitations")));
    }

    private static void binding(JsonNode value, EvidenceContext context) {
        if (!context.gameId().equals(uuid(value.path("gameId"))) || !context.profileId().equals(uuid(value.path("hardwareProfileId")))
                || context.profileVersion() != count(value.path("hardwareProfileVersion"))) throw invalid();
        var expected = java.util.Map.of("contractVersion", "fps-evidence-v1", "resolution", context.resolution(),
                "graphicsPreset", context.graphicsPreset(), "renderingMode", context.renderingMode(), "source", "AUTHENTICATED_DEVICE",
                "contextVerification", "OWNER_SNAPSHOT_DEVICE_DECLARATION", "method", METHOD, "confidenceCeiling", "LOW");
        expected.forEach((key, text) -> {
            if (!value.path(key).isString() || !text.equals(value.path(key).asString())) throw invalid();
        });
    }

    private static void counts(long considered, long sessions, long observed, long inliers, long excluded) {
        if (considered > 20 || sessions > considered || observed > considered * 600 || inliers > observed
                || excluded != observed - inliers || inliers < sessions * 60) throw invalid();
    }

    private static void times(Instant first, Instant last, long sessions, Instant now) {
        if (sessions == 0) {
            if (first != null || last != null) throw invalid();
            return;
        }
        if (first == null || last == null || first.isAfter(last) || Duration.between(first, last).getSeconds() < 60
                || first.isBefore(now.minus(Duration.ofDays(30)).minusSeconds(30)) || last.isAfter(now.plusSeconds(30))) throw invalid();
    }

    private static void range(boolean sufficient, long sessions, Double lower, Double upper) {
        if (!sufficient) {
            if (lower != null || upper != null || sessions >= 3) throw invalid();
        } else if (sessions < 3 || lower == null || upper == null || lower <= 0 || upper > 1000 || lower > upper) throw invalid();
    }

    private static List<String> limitations(JsonNode items) {
        if (!items.isArray() || items.size() != LIMITATIONS.size()) throw invalid();
        List<String> result = new ArrayList<>();
        for (JsonNode item : items) {
            if (!item.isString() || !LIMITATIONS.contains(item.asString()) || result.contains(item.asString())) throw invalid();
            result.add(item.asString());
        }
        return List.copyOf(result);
    }
}

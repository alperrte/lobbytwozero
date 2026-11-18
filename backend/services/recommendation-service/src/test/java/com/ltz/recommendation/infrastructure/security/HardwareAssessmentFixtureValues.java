package com.ltz.recommendation.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;

/** HardwareAssessmentApiIntegrationTest sahte kaynak yanıtları; mod adına göre bozuk/geçerli değerler üretir. */
final class HardwareAssessmentFixtureValues {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final List<String> BASE_LIMITATIONS = List.of("DEVICE_CONTEXT_NOT_ATTESTED",
            "GAME_BUILD_AND_BACKGROUND_LOAD_UNCONTROLLED", "HISTORICAL_RANGE_NOT_CURRENT_MEASUREMENT");

    private HardwareAssessmentFixtureValues() { }

    static List<Object> components(String mode) {
        boolean below = mode.equals("below-minimum");
        List<Object> components = new ArrayList<>(List.of(
                component(mode, "CPU", "CPU MODEL", "{\"socket\":\"AM4\"}"),
                component(mode, "GPU", "GPU MODEL", "{\"capacityGb\":8}"),
                component(mode, "RAM", "manual", "{\"capacityGb\":" + (below ? 4 : 16) + ",\"memoryType\":\"DDR4\"}"),
                component(mode, "STORAGE", "manual", "{\"capacityGb\":" + (below ? 1 : 512) + "}"),
                component(mode, "MOTHERBOARD", "manual", "{\"memoryType\":\"DDR4\",\"socket\":\""
                        + (mode.equals("incompatible-socket") ? "LGA1700" : "AM4") + "\"}")));
        if (mode.equals("empty-profile")) components.clear();
        if (mode.equals("duplicate-component")) components.add(components.getFirst());
        return components;
    }

    private static Map<String, Object> component(String mode, String category, String model, String specs) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", UUID.nameUUIDFromBytes(category.getBytes(StandardCharsets.UTF_8)));
        result.put("category", category);
        result.put("modelName", model);
        result.put("quantity", 1);
        result.put("brandName", null);
        result.put("brandId", null);
        result.put("source", "MANUAL");
        result.put("specifications", JSON.readTree(mode.equals("invalid-capacity") ? "{\"capacityGb\":-1}" : specs));
        return result;
    }

    static Map<String, Object> evidence(String mode, UUID game, UUID profile) {
        boolean sufficient = !mode.equals("ev-insufficient");
        long sessions = sufficient ? 3 : 2;
        long observed = sessions * 110;
        Instant now = Instant.now();
        Map<String, Object> body = new HashMap<>();
        body.put("contractVersion", "fps-evidence-v1");
        body.put("gameId", game);
        body.put("hardwareProfileId", profile);
        body.put("hardwareProfileVersion", mode.equals("ev-wrong-version") ? 6 : 7);
        body.put("resolution", mode.equals("ev-wrong-context") ? "3840x2160" : "1920x1080");
        body.put("graphicsPreset", "HIGH");
        body.put("renderingMode", "NATIVE_RASTER");
        body.put("status", sufficient ? "SUFFICIENT" : "INSUFFICIENT_DATA");
        body.put("source", mode.equals("ev-wrong-source") ? "MANUAL" : "AUTHENTICATED_DEVICE");
        body.put("contextVerification", "OWNER_SNAPSHOT_DEVICE_DECLARATION");
        body.put("method", mode.equals("ev-wrong-method") ? "MEAN" : "SESSION_MEDIAN_P10_P90_IQR_1_5_V1");
        body.put("confidenceCeiling", "LOW");
        body.put("consideredSessionCount", 3);
        body.put("sessionCount", mode.equals("ev-sufficient-two-sessions") ? 2 : sessions);
        body.put("observedSampleCount", observed);
        body.put("inlierSampleCount", mode.equals("ev-bad-counts") ? observed + 5 : observed);
        body.put("excludedSampleCount", 0);
        body.putAll(references(mode, sufficient));
        body.put("firstMeasuredAt", now.minus(3, ChronoUnit.DAYS).toString());
        body.put("lastMeasuredAt", now.minus(1, ChronoUnit.DAYS).toString());
        body.put("windowLimited", false);
        body.put("limitations", limitations(mode));
        return body;
    }

    private static Map<String, Object> references(String mode, boolean sufficient) {
        Map<String, Object> result = new HashMap<>();
        boolean inverted = mode.equals("ev-inverted");
        result.put("lowerReferenceFps", sufficient ? (inverted ? 58.2 : 51.4) : null);
        result.put("upperReferenceFps", sufficient ? (inverted ? 51.4 : 58.2) : null);
        return result;
    }

    private static List<String> limitations(String mode) {
        if (mode.equals("ev-missing-limitation")) return BASE_LIMITATIONS;
        List<String> all = new ArrayList<>(BASE_LIMITATIONS);
        all.add("MAX_20_SESSIONS_600_SAMPLES_30_DAYS");
        return all;
    }
}

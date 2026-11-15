package com.ltz.recommendation.infrastructure.client;

import static com.ltz.recommendation.infrastructure.client.BoundedPythonProcess.failure;

import com.ltz.recommendation.application.port.AssistantEngine.Input;
import java.util.List;
import java.util.Map;

/** Yanıtın kapalı şablon/aksiyon sözleşmesi; Python dışına serbest aksiyon taşımaz. */
final class AssistantPlan {
    private static final Map<String, List<String>> STEPS = Map.of(
            "MISSING_INPUT", List.of(),
            "GAME_SELECTION", List.of("OPEN_GAME_DISCOVERY", "FILTER_GENRE", "VERIFY_GAME_REQUIREMENTS"),
            "HARDWARE_DEFICIT", List.of("REVIEW_MINIMUM_DEFICIT", "COMPARE_RECOMMENDED_LEVEL"),
            "HARDWARE_UNKNOWN", List.of("CHECK_UNKNOWN_REQUIREMENTS", "COMPARE_RECOMMENDED_LEVEL", "MEASURE_IN_GAME"),
            "HISTORY_GUIDE", List.of("OPEN_RECOMMENDATION_HISTORY"),
            "FEEDBACK_GUIDE", List.of("OPEN_RECOMMENDATION_HISTORY", "SELECT_OWN_ITEM", "SEND_FEEDBACK"));

    private AssistantPlan() { }

    static void validate(Input input, String status, String template, List<String> steps) {
        String expected = expected(input);
        String expectedStatus = input.missingFields().isEmpty()
                ? "HARDWARE".equals(input.topic()) ? "INSUFFICIENT_DATA" : "GUIDANCE" : "NEEDS_INPUT";
        if (!expected.equals(template) || !expectedStatus.equals(status) || !STEPS.get(expected).equals(steps)) {
            throw failure("ASSISTANT_INVALID_OUTPUT");
        }
    }

    private static String expected(Input input) {
        if (!input.missingFields().isEmpty()) return "MISSING_INPUT";
        return switch (input.topic()) {
            case "GAME" -> "GAME_SELECTION";
            case "HARDWARE" -> "BELOW".equals(input.minimum()) || "INCOMPATIBLE".equals(input.compatibility())
                    ? "HARDWARE_DEFICIT" : "HARDWARE_UNKNOWN";
            case "LTZ_USAGE" -> "VIEW_HISTORY".equals(input.intent()) ? "HISTORY_GUIDE" : "FEEDBACK_GUIDE";
            default -> throw failure("ASSISTANT_INVALID_OUTPUT");
        };
    }
}

package com.ltz.recommendation.infrastructure.client;

import static com.ltz.recommendation.infrastructure.client.BoundedPythonProcess.failure;

import com.ltz.recommendation.application.port.AssistantEngine.Input;
import com.ltz.recommendation.application.port.AssistantEngine.Reply;
import java.util.List;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

final class AssistantReplyReader {
    private static final Set<String> FIELDS = Set.of("contractVersion", "algorithmVersion", "topic", "intent", "locale",
            "status", "template", "message", "steps", "missingFields");
    private static final Set<String> TEMPLATES = Set.of("MISSING_INPUT", "GAME_SELECTION", "HARDWARE_DEFICIT",
            "HARDWARE_UNKNOWN", "HISTORY_GUIDE", "FEEDBACK_GUIDE");
    private static final Set<String> STEPS = Set.of("OPEN_GAME_DISCOVERY", "FILTER_GENRE", "VERIFY_GAME_REQUIREMENTS",
            "REVIEW_MINIMUM_DEFICIT", "COMPARE_RECOMMENDED_LEVEL", "CHECK_UNKNOWN_REQUIREMENTS", "MEASURE_IN_GAME",
            "OPEN_RECOMMENDATION_HISTORY", "SELECT_OWN_ITEM", "SEND_FEEDBACK");
    private static final JsonMapper JSON = JsonMapper.builder().enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    private AssistantReplyReader() { }

    static Reply read(byte[] bytes, Input input) {
        try {
            JsonNode value = JSON.readTree(bytes);
            if (value == null || !value.isObject() || !value.propertyNames().equals(FIELDS)) throw failure("ASSISTANT_INVALID_OUTPUT");
            binding(value, input);
            String status = text(value, "status");
            String template = text(value, "template");
            if (!Set.of("GUIDANCE", "NEEDS_INPUT", "INSUFFICIENT_DATA").contains(status) || !TEMPLATES.contains(template)) {
                throw failure("ASSISTANT_INVALID_OUTPUT");
            }
            var steps = list(value.path("steps"), STEPS, 4);
            AssistantPlan.validate(input, status, template, steps);
            var missing = list(value.path("missingFields"), Set.of("GENRE", "HARDWARE_PROFILE_ID", "GAME_ID", "PLATFORM"), 4);
            if (!missing.equals(input.missingFields())) throw failure("ASSISTANT_INVALID_OUTPUT");
            String message = text(value, "message");
            validateMessage(message);
            return new Reply(input.contractVersion(), "assistant-rules-v1", input.topic(), input.intent(), input.locale(),
                    status, template, message, steps, missing);
        } catch (tools.jackson.core.JacksonException exception) { throw failure("ASSISTANT_INVALID_OUTPUT", exception); }
    }

    private static void validateMessage(String message) {
        if (message.isBlank() || message.length() > 1500 || message.chars().anyMatch(Character::isISOControl)
                || message.contains("<") || message.contains(">")) throw failure("ASSISTANT_INVALID_OUTPUT");
    }

    private static void binding(JsonNode value, Input input) {
        if (!"assistant-v1".equals(text(value, "contractVersion")) || !"assistant-rules-v1".equals(text(value, "algorithmVersion"))
                || !input.topic().equals(text(value, "topic")) || !input.intent().equals(text(value, "intent"))
                || !input.locale().equals(text(value, "locale"))) throw failure("ASSISTANT_INVALID_OUTPUT");
    }

    private static String text(JsonNode value, String field) {
        JsonNode node = value.path(field);
        if (!node.isString()) throw failure("ASSISTANT_INVALID_OUTPUT");
        return node.asString();
    }

    private static List<String> list(JsonNode value, Set<String> allowed, int limit) {
        if (!value.isArray() || value.size() > limit) throw failure("ASSISTANT_INVALID_OUTPUT");
        var result = new java.util.ArrayList<String>();
        for (JsonNode item : value) {
            if (!item.isString() || !allowed.contains(item.asString()) || result.contains(item.asString())) {
                throw failure("ASSISTANT_INVALID_OUTPUT");
            }
            result.add(item.asString());
        }
        return List.copyOf(result);
    }
}

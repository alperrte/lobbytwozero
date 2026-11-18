package com.ltz.game.api.dto.request;

import com.ltz.game.domain.exception.InvalidGameRequestException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;

public record RecommendationMetadataBatchRequest(List<UUID> gameIds) {
    private static final int MAX_IDS = 100;
    private static final String INVALID_CODE = "INVALID_GAME_IDS";
    private static final ObjectReader JSON = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY, DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build().reader();

    public static RecommendationMetadataBatchRequest parse(byte[] body) {
        JsonNode root;
        try {
            root = JSON.readTree(body);
        } catch (JacksonException _) {
            throw new InvalidGameRequestException(INVALID_CODE);
        }
        if (root == null || !root.isObject() || root.size() != 1) {
            throw new InvalidGameRequestException(INVALID_CODE);
        }
        JsonNode ids = root.path("gameIds");
        if (!ids.isArray() || ids.isEmpty() || ids.size() > MAX_IDS) {
            throw new InvalidGameRequestException(INVALID_CODE);
        }
        var unique = new LinkedHashSet<UUID>();
        for (JsonNode node : ids) {
            if (!unique.add(canonicalId(node))) {
                throw new InvalidGameRequestException("DUPLICATE_GAME_ID");
            }
        }
        return new RecommendationMetadataBatchRequest(List.copyOf(unique));
    }

    private static UUID canonicalId(JsonNode node) {
        if (!node.isTextual() || node.asText().length() != 36) {
            throw new InvalidGameRequestException(INVALID_CODE);
        }
        String raw = node.asText();
        UUID id;
        try {
            id = UUID.fromString(raw);
        } catch (IllegalArgumentException _) {
            throw new InvalidGameRequestException(INVALID_CODE);
        }
        if (!id.toString().equalsIgnoreCase(raw)) {
            throw new InvalidGameRequestException(INVALID_CODE);
        }
        return id;
    }
}

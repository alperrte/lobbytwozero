package com.ltz.recommendation.infrastructure.client;

import com.ltz.recommendation.application.port.HardwareAssessmentSource.Requirement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/** Game GAME-15 sistem gereksinimi alanlarını doğrulayarak okur; bozuk şema 503 SOURCE_UNAVAILABLE'dır. */
final class GameRequirementReader {
    private GameRequirementReader() { }

    static List<Requirement> requirements(JsonNode items, String platform) {
        if (!items.isArray() || items.size() > 32) throw RecommendationHttpClient.unavailable();
        List<Requirement> result = new ArrayList<>();
        Set<String> levels = new HashSet<>();
        for (JsonNode item : items) {
            String itemPlatform = HttpGameMetadataSource.text(item.path("platform"), 32, true);
            String level = HttpGameMetadataSource.text(item.path("level"), 32, true);
            if (!"MINIMUM".equals(level) && !"RECOMMENDED".equals(level)) throw RecommendationHttpClient.unavailable();
            if (!platform.equalsIgnoreCase(itemPlatform)) continue;
            if (!levels.add(level)) throw RecommendationHttpClient.unavailable();
            result.add(new Requirement(level, field(item.path("cpu")), field(item.path("gpu")),
                    intField(item.path("ramMb")), intField(item.path("storageMb")), field(item.path("os"))));
        }
        return List.copyOf(result);
    }

    /** Game alanı {value,state}: değer yalnız state AVAILABLE iken kullanılır; UNKNOWN/UNCOMPARABLE → null (UNKNOWN uyumluluk). */
    private static String field(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) return null;
        boolean available = available(node);
        String value = HttpGameMetadataSource.text(node.path("value"), 4096, false);
        return available ? value : null;
    }

    private static Integer intField(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) return null;
        boolean available = available(node);
        Integer value = HttpGameMetadataSource.integer(node.path("value"));
        return available ? value : null;
    }

    private static boolean available(JsonNode node) {
        if (!node.isObject()) throw RecommendationHttpClient.unavailable();
        String state = HttpGameMetadataSource.text(node.path("state"), 32, true);
        if (!"AVAILABLE".equals(state) && !"UNKNOWN".equals(state) && !"UNCOMPARABLE".equals(state)) {
            throw RecommendationHttpClient.unavailable();
        }
        return "AVAILABLE".equals(state);
    }
}

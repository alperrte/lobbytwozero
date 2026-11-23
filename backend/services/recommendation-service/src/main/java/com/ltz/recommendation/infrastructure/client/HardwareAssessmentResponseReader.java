package com.ltz.recommendation.infrastructure.client;

import static com.ltz.recommendation.infrastructure.client.AssessmentValues.count;
import static com.ltz.recommendation.infrastructure.client.AssessmentValues.integer;
import static com.ltz.recommendation.infrastructure.client.AssessmentValues.invalid;
import static com.ltz.recommendation.infrastructure.client.AssessmentValues.text;
import static com.ltz.recommendation.infrastructure.client.AssessmentValues.uuid;

import com.ltz.recommendation.application.port.HardwareAssessmentSource.Component;
import com.ltz.recommendation.application.port.HardwareAssessmentSource.Profile;
import com.ltz.recommendation.application.port.HardwareAssessmentSource.Requirement;
import com.ltz.recommendation.application.port.HardwareAssessmentSource.Specs;
import com.ltz.recommendation.domain.exception.RecommendationRequestException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

final class HardwareAssessmentResponseReader {
    private HardwareAssessmentResponseReader() { }

    static Profile profile(JsonNode reply, UUID profileId) {
        var profiles = reply.path("profiles");
        if (!profiles.isArray() || profiles.size() != 1) throw invalid();
        JsonNode profile = profiles.get(0);
        if (!profileId.equals(uuid(profile.path("profileId")))) throw invalid();
        long version = count(profile.path("profileVersion"));
        JsonNode components = profile.path("components");
        validatePage(profile.path("page"), components);
        List<Component> result = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (JsonNode component : components) {
            var parsed = component(component);
            if (!seen.add(parsed.id())) throw invalid();
            result.add(parsed);
        }
        return new Profile(profileId, version, result);
    }

    private static void validatePage(JsonNode page, JsonNode components) {
        if (!components.isArray() || components.size() > 100 || count(page.path("number")) != 0
                || count(page.path("size")) != 100 || !page.path("hasNext").isBoolean()) throw invalid();
        if (page.path("hasNext").asBoolean()) throw new RecommendationRequestException(503, "PROFILE_WINDOW_EXCEEDED");
        if (count(page.path("totalElements")) != components.size()) throw invalid();
    }

    private static Component component(JsonNode item) {
        UUID id = uuid(item.path("id"));
        String category = text(item.path("category"), 32);
        Integer quantity = integer(item.path("quantity"), 1024);
        if (category == null || quantity == null) throw invalid();
        JsonNode specs = item.path("specifications");
        return new Component(id, category, text(item.path("modelName"), 512), quantity,
                new Specs(integer(specs.path("capacityGb"), 1048576), text(specs.path("memoryType"), 32), text(specs.path("socket"), 32)));
    }

    static List<Requirement> requirements(JsonNode reply, UUID gameId, String platform) {
        requireAccessibleGame(reply, gameId);
        JsonNode items = reply.path("systemRequirements");
        if (items.isNull() || items.isMissingNode()) return List.of();
        if (!items.isArray() || items.size() > 32) throw invalid();
        List<Requirement> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode item : items) {
            String itemPlatform = text(item.path("platform"), 32);
            if (itemPlatform == null) throw invalid();
            if (!platform.equals(itemPlatform)) continue;
            Requirement requirement = requirement(item);
            if (!seen.add(requirement.level())) throw invalid();
            result.add(requirement);
        }
        return List.copyOf(result);
    }

    private static void requireAccessibleGame(JsonNode reply, UUID gameId) {
        if (!gameId.equals(uuid(reply.path("gameId")))) throw invalid();
        if (!reply.path("adult").isBoolean() || reply.path("adult").asBoolean()
                || !reply.path("delisted").isBoolean() || reply.path("delisted").asBoolean()) {
            throw new RecommendationRequestException(404, "GAME_UNAVAILABLE");
        }
    }

    private static Requirement requirement(JsonNode item) {
        String level = text(item.path("level"), 32);
        if (level == null || !Set.of("MINIMUM", "RECOMMENDED").contains(level)) throw invalid();
        return new Requirement(level, text(item.path("cpu"), 4096), text(item.path("gpu"), 4096),
                integer(item.path("ramMb"), Integer.MAX_VALUE), integer(item.path("storageMb"), Integer.MAX_VALUE),
                text(item.path("os"), 4096));
    }
}

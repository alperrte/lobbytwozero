package com.ltz.recommendation.infrastructure.client;

import com.ltz.recommendation.application.port.GameMetadataSource;
import com.ltz.recommendation.application.port.HardwareAssessmentSource.Requirement;
import com.ltz.recommendation.application.port.PersonalizationSource.Actor;
import com.ltz.recommendation.domain.exception.RecommendationRequestException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class HttpGameMetadataSource implements GameMetadataSource {
    private static final int MAX_IDS = 100;
    private final RecommendationHttpClient http;
    private final RecommendationServiceTokenClient tokens;
    private final String gameUrl;

    public HttpGameMetadataSource(RecommendationHttpClient http, RecommendationServiceTokenClient tokens,
            @Value("${GAME_SERVICE_URL:}") String gameUrl) {
        this.http = http;
        this.tokens = tokens;
        this.gameUrl = gameUrl;
    }

    @Override
    public Batch requirements(Actor actor, List<UUID> gameIds, String platform) {
        if (gameIds.isEmpty() || gameIds.size() > MAX_IDS || new HashSet<>(gameIds).size() != gameIds.size()) {
            throw new IllegalArgumentException("Invalid game batch");
        }
        JsonNode reply;
        try {
            reply = http.postResource(RecommendationHttpClient.origin(gameUrl)
                            .resolve("/internal/v1/recommendation/game-metadata/batch"),
                    Map.of("gameIds", gameIds), tokens.gameMetadataToken(actor.accountId()));
        } catch (RecommendationRequestException exception) {
            if (exception.status() == 404) throw RecommendationHttpClient.unavailable();
            throw exception;
        }
        return read(reply, gameIds, platform);
    }

    static Batch read(JsonNode reply, List<UUID> requested, String platform) {
        JsonNode items = reply.path("items");
        JsonNode unavailable = reply.path("unavailable");
        if (!items.isArray() || !unavailable.isArray() || items.size() + unavailable.size() != requested.size()) {
            throw RecommendationHttpClient.unavailable();
        }
        Set<UUID> asked = new HashSet<>(requested);
        Set<UUID> seen = new HashSet<>();
        Map<UUID, List<Requirement>> byGame = new LinkedHashMap<>();
        for (JsonNode item : items) {
            UUID id = uuid(item.path("gameId"));
            if (!asked.contains(id) || !seen.add(id)) throw RecommendationHttpClient.unavailable();
            byGame.put(id, requirements(item.path("requirements"), platform));
        }
        Set<UUID> missing = new HashSet<>();
        for (JsonNode item : unavailable) {
            UUID id = uuid(item);
            if (!asked.contains(id) || !seen.add(id)) throw RecommendationHttpClient.unavailable();
            missing.add(id);
        }
        return new Batch(byGame, missing);
    }

    private static List<Requirement> requirements(JsonNode items, String platform) {
        if (!items.isArray() || items.size() > 32) throw RecommendationHttpClient.unavailable();
        List<Requirement> result = new ArrayList<>();
        Set<String> levels = new HashSet<>();
        for (JsonNode item : items) {
            String itemPlatform = text(item.path("platform"), 32, true);
            String level = text(item.path("level"), 32, true);
            if (!"MINIMUM".equals(level) && !"RECOMMENDED".equals(level)) throw RecommendationHttpClient.unavailable();
            if (!platform.equals(itemPlatform)) continue;
            if (!levels.add(level)) throw RecommendationHttpClient.unavailable();
            result.add(new Requirement(level, text(item.path("cpu"), 4096, false), text(item.path("gpu"), 4096, false),
                    integer(item.path("ramMb")), integer(item.path("storageMb")), text(item.path("os"), 4096, false)));
        }
        return List.copyOf(result);
    }

    private static String text(JsonNode value, int max, boolean required) {
        if (value.isNull() || value.isMissingNode()) {
            if (required) throw RecommendationHttpClient.unavailable();
            return null;
        }
        if (!value.isString() || value.asString().length() > max || (required && value.asString().isBlank())) {
            throw RecommendationHttpClient.unavailable();
        }
        return value.asString();
    }

    private static Integer integer(JsonNode value) {
        if (value.isNull() || value.isMissingNode()) return null;
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.asInt() < 0) throw RecommendationHttpClient.unavailable();
        return value.asInt();
    }

    private static UUID uuid(JsonNode value) {
        if (!value.isString()) throw RecommendationHttpClient.unavailable();
        try {
            UUID id = UUID.fromString(value.asString());
            if (!id.toString().equalsIgnoreCase(value.asString())) throw RecommendationHttpClient.unavailable();
            return id;
        } catch (IllegalArgumentException _) { throw RecommendationHttpClient.unavailable(); }
    }
}

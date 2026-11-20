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
    private final String gameUrl;

    public HttpGameMetadataSource(RecommendationHttpClient http,
            @Value("${GAME_SERVICE_URL:}") String gameUrl) {
        this.http = http;
        this.gameUrl = gameUrl;
    }

    @Override
    public Batch requirements(Actor actor, List<UUID> gameIds, String platform) {
        return read(postBatch(gameIds), gameIds, platform);
    }

    @Override
    public Map<UUID, List<String>> genres(List<UUID> gameIds) {
        return readGenres(postBatch(gameIds), gameIds);
    }

    private JsonNode postBatch(List<UUID> gameIds) {
        if (gameIds.isEmpty() || gameIds.size() > MAX_IDS || new HashSet<>(gameIds).size() != gameIds.size()) {
            throw new IllegalArgumentException("Invalid game batch");
        }
        RecommendationRequestException failure;
        try {
            return http.postResource(RecommendationHttpClient.origin(gameUrl)
                    .resolve("/api/v1/game/games/recommendation-metadata/batch"), Map.of("gameIds", gameIds), null);
        } catch (RecommendationRequestException exception) {
            failure = exception;
        }
        if (failure.status() == 404) throw RecommendationHttpClient.unavailable();
        throw failure;
    }

    /** items/unavailable dizilerini ve toplam sayıyı doğrular. */
    private static void checkEnvelope(JsonNode reply, List<UUID> requested) {
        JsonNode items = reply.path("items");
        JsonNode unavailable = reply.path("unavailable");
        if (!items.isArray() || !unavailable.isArray() || items.size() + unavailable.size() != requested.size()) {
            throw RecommendationHttpClient.unavailable();
        }
    }

    /** Kimlik istenenler arasında olmalı ve yanıtta bir kez görünmelidir. */
    private static UUID claim(JsonNode value, Set<UUID> asked, Set<UUID> seen) {
        UUID id = HttpPersonalizationSource.uuid(value);
        if (!asked.contains(id) || !seen.add(id)) throw RecommendationHttpClient.unavailable();
        return id;
    }

    private static Set<UUID> readUnavailable(JsonNode reply, Set<UUID> asked, Set<UUID> seen) {
        Set<UUID> missing = new HashSet<>();
        for (JsonNode item : reply.path("unavailable")) missing.add(claim(item, asked, seen));
        return missing;
    }

    static Map<UUID, List<String>> readGenres(JsonNode reply, List<UUID> requested) {
        checkEnvelope(reply, requested);
        Set<UUID> asked = new HashSet<>(requested);
        Set<UUID> seen = new HashSet<>();
        Map<UUID, List<String>> result = new LinkedHashMap<>();
        for (JsonNode item : reply.path("items")) {
            UUID id = claim(item.path("gameId"), asked, seen);
            boolean hidden = bool(item.path("adult")) || !bool(item.path("visible"));
            List<String> codes = genreCodes(item.path("genres"));
            if (!hidden) result.put(id, codes);
        }
        readUnavailable(reply, asked, seen);
        return result;
    }

    private static List<String> genreCodes(JsonNode genres) {
        if (!genres.isArray() || genres.size() > 32) throw RecommendationHttpClient.unavailable();
        List<String> codes = new ArrayList<>();
        for (JsonNode genre : genres) codes.add(text(genre.path("code"), 64, true));
        return List.copyOf(codes);
    }

    static Batch read(JsonNode reply, List<UUID> requested, String platform) {
        checkEnvelope(reply, requested);
        Set<UUID> asked = new HashSet<>(requested);
        Set<UUID> seen = new HashSet<>();
        Map<UUID, List<Requirement>> byGame = new LinkedHashMap<>();
        Set<UUID> hidden = new HashSet<>();
        for (JsonNode item : reply.path("items")) {
            UUID id = claim(item.path("gameId"), asked, seen);
            boolean skip = bool(item.path("adult")) || !bool(item.path("visible"));
            List<Requirement> parsed = GameRequirementReader.requirements(item.path("systemRequirements"), platform);
            // Yetişkin ya da görünmez oyun öneriye girmez; kaynak tarafı zaten ayıklar, burada ikinci savunma.
            if (skip) hidden.add(id);
            else byGame.put(id, parsed);
        }
        Set<UUID> missing = new HashSet<>(hidden);
        missing.addAll(readUnavailable(reply, asked, seen));
        return new Batch(byGame, missing);
    }

    static boolean bool(JsonNode value) {
        if (!value.isBoolean()) throw RecommendationHttpClient.unavailable();
        return value.asBoolean();
    }

    static String text(JsonNode value, int max, boolean required) {
        if (value.isNull() || value.isMissingNode()) {
            if (required) throw RecommendationHttpClient.unavailable();
            return null;
        }
        if (!value.isString() || value.asString().length() > max || (required && value.asString().isBlank())) {
            throw RecommendationHttpClient.unavailable();
        }
        return value.asString();
    }

    static Integer integer(JsonNode value) {
        if (value.isNull() || value.isMissingNode()) return null;
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.asInt() < 0) throw RecommendationHttpClient.unavailable();
        return value.asInt();
    }
}

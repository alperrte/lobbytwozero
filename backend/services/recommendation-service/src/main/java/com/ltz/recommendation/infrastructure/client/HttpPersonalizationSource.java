package com.ltz.recommendation.infrastructure.client;

import com.ltz.recommendation.application.port.PersonalizationSource;
import com.ltz.recommendation.domain.exception.RecommendationRequestException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

@Component
public class HttpPersonalizationSource implements PersonalizationSource {
    private final RecommendationHttpClient http;
    private final RecommendationServiceTokenClient tokens;
    private final String userUrl;
    private final String gameUrl;
    private final String libraryUrl;

    public HttpPersonalizationSource(RecommendationHttpClient http, RecommendationServiceTokenClient tokens,
            @Value("${USER_SERVICE_URL:}") String userUrl, @Value("${GAME_SERVICE_URL:}") String gameUrl,
            @Value("${LIBRARY_SERVICE_URL:}") String libraryUrl) {
        this.http = http;
        this.tokens = tokens;
        this.userUrl = userUrl;
        this.gameUrl = gameUrl;
        this.libraryUrl = libraryUrl;
    }

    @Override
    public void requireConsent(Actor actor) {
        var reply = http.get(RecommendationHttpClient.origin(userUrl).resolve("/api/v1/user/me/consents"), actor.bearer());
        if (!reply.path("recommendation").isBoolean()) throw RecommendationHttpClient.unavailable();
        if (!reply.path("recommendation").asBoolean()) throw new RecommendationRequestException(403, "CONSENT_REQUIRED");
        // The existing account-bound grant checks ACTIVE in Auth; this is not session introspection.
        tokens.libraryToken(actor.accountId());
    }

    @Override
    public List<Match> candidates(Actor actor, Filters filters) {
        var profile = http.get(RecommendationHttpClient.origin(userUrl).resolve("/api/v1/user/me"), actor.bearer());
        if (!actor.accountId().equals(uuid(profile.path("userId")))) throw RecommendationHttpClient.unavailable();
        List<String> genres = strings(profile.path("favoriteGenres"));
        if (filters.genre() != null) genres = genres.stream().filter(filters.genre()::equals).toList();
        if (genres.isEmpty()) return List.of();
        Set<UUID> excluded = library(actor);
        Map<UUID, Set<String>> matches = new HashMap<>();
        for (String genre : genres) {
            for (UUID id : discovery(actor, filters, genre)) {
                if (!excluded.contains(id)) matches.computeIfAbsent(id, ignored -> new LinkedHashSet<>()).add(genre);
            }
        }
        List<Match> result = new ArrayList<>();
        for (var entry : matches.entrySet()) {
            result.add(new Match(entry.getKey(), entry.getValue().size() * 10000 / genres.size(),
                    entry.getValue().stream().sorted().toList()));
        }
        return List.copyOf(result);
    }

    private List<UUID> discovery(Actor actor, Filters filters, String genre) {
        var uri = UriComponentsBuilder.fromUri(RecommendationHttpClient.origin(gameUrl))
                .path("/api/v1/game/games").queryParam("page", 0).queryParam("size", 50)
                .queryParam("genre", genre).queryParam("sort", "TITLE").queryParam("direction", "ASC");
        if (filters.platform() != null) uri.queryParam("platform", filters.platform());
        JsonNode reply = http.get(uri.build().encode().toUri(), actor.bearer());
        JsonNode items = reply.path("items");
        if (!items.isArray() || items.size() > 50 || !reply.path("page").isIntegralNumber()
                || reply.path("page").asInt(-1) != 0 || !reply.path("includeAdultApplied").isBoolean()
                || reply.path("includeAdultApplied").asBoolean()) throw RecommendationHttpClient.unavailable();
        List<UUID> ids = new ArrayList<>();
        for (JsonNode item : items) {
            JsonNode game = item.path("game");
            UUID id = uuid(game.path("gameId"));
            // Query membership is the source proof of genre/platform; unknown adult state is never promoted.
            if ("MAIN_GAME".equals(game.path("gameType").asString()) && item.path("adult").isBoolean()
                    && !item.path("adult").asBoolean()) ids.add(id);
        }
        return ids;
    }

    private Set<UUID> library(Actor actor) {
        String bearer = tokens.libraryToken(actor.accountId());
        Set<UUID> excluded = new HashSet<>();
        Set<UUID> seen = new HashSet<>();
        long total = Long.MAX_VALUE;
        for (int page = 0; page < 10 && page * 50L < total; page++) {
            URI uri = UriComponentsBuilder.fromUri(RecommendationHttpClient.origin(libraryUrl))
                    .path("/internal/v1/library/users/{id}/recommendation-signals")
                    .queryParam("page", page).queryParam("size", 50).buildAndExpand(actor.accountId()).toUri();
            var reply = http.get(uri, bearer);
            total = validateLibraryPage(reply, page, total);
            addLibraryExclusions(reply.path("items"), seen, excluded);
        }
        return excluded;
    }

    static long validateLibraryPage(JsonNode reply, int page, long previousTotal) {
        var items = reply.path("items");
        long total = reply.path("totalElements").asLong(-1);
        if (!items.isArray() || !reply.path("totalElements").isIntegralNumber()
                || !reply.path("page").isIntegralNumber() || !reply.path("size").isIntegralNumber()
                || total < 0 || total > 500 || reply.path("page").asInt(-1) != page || reply.path("size").asInt(-1) != 50) {
            throw RecommendationHttpClient.unavailable();
        }
        if ((page > 0 && total != previousTotal) || items.size() != Math.clamp(total - page * 50L, 0, 50)) {
            throw RecommendationHttpClient.unavailable();
        }
        return total;
    }

    private static void addLibraryExclusions(JsonNode items, Set<UUID> seen, Set<UUID> excluded) {
        for (var item : items) {
            if (!item.path("favorite").isBoolean() || !item.path("completed").isBoolean()) throw RecommendationHttpClient.unavailable();
            UUID id = uuid(item.path("gameId"));
            if (!seen.add(id)) throw RecommendationHttpClient.unavailable();
            if (item.path("favorite").asBoolean() || item.path("completed").asBoolean()) excluded.add(id);
        }
    }

    static UUID uuid(JsonNode value) {
        if (!value.isString()) throw RecommendationHttpClient.unavailable();
        try {
            UUID id = UUID.fromString(value.asString());
            if (!id.toString().equalsIgnoreCase(value.asString())) throw RecommendationHttpClient.unavailable();
            return id;
        } catch (IllegalArgumentException _) { throw RecommendationHttpClient.unavailable(); }
    }

    private static List<String> strings(JsonNode value) {
        if (!value.isArray() || value.size() > 10) throw RecommendationHttpClient.unavailable();
        List<String> values = new ArrayList<>();
        for (var item : value) {
            if (!item.isString() || item.asString().isBlank() || item.asString().length() > 64
                    || item.asString().chars().anyMatch(Character::isISOControl)) throw RecommendationHttpClient.unavailable();
            values.add(item.asString());
        }
        return values.stream().distinct().sorted().toList();
    }
}

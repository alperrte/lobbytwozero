package com.ltz.recommendation.infrastructure.client;

import com.ltz.recommendation.application.port.GameMetadataSource;
import com.ltz.recommendation.application.port.PersonalRatingSource;
import com.ltz.recommendation.application.port.PersonalizationSource.Actor;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

/** Review GET /api/v1/review/ratings/mine (kullanıcı token'ı) + Game GAME-15 tür kodları; kaynak hatasında fail-closed. */
@Component
public class HttpPersonalRatingSource implements PersonalRatingSource {
    private static final int PAGE_SIZE = 50;
    private static final int MAX_PAGES = 10;
    private static final int MAX_CURSOR = 200;
    private static final int GAME_BATCH = 100;
    private final RecommendationHttpClient http;
    private final GameMetadataSource games;
    private final String reviewUrl;

    public HttpPersonalRatingSource(RecommendationHttpClient http, GameMetadataSource games,
            @Value("${REVIEW_SERVICE_URL:}") String reviewUrl) {
        this.http = http;
        this.games = games;
        this.reviewUrl = reviewUrl;
    }

    @Override
    public Map<String, Integer> genreAffinity(Actor actor) {
        Map<UUID, Integer> ratings = ratings(actor);
        if (ratings.isEmpty()) return Map.of();
        List<UUID> ids = new ArrayList<>(ratings.keySet());
        Map<String, long[]> sums = new HashMap<>();
        for (int from = 0; from < ids.size(); from += GAME_BATCH) {
            List<UUID> chunk = ids.subList(from, Math.min(ids.size(), from + GAME_BATCH));
            games.genres(List.copyOf(chunk)).forEach((id, genres) -> {
                for (String genre : genres.stream().map(code -> code.toLowerCase(Locale.ROOT)).distinct().toList()) {
                    long[] sum = sums.computeIfAbsent(genre, ignored -> new long[2]);
                    sum[0] += ratings.get(id);
                    sum[1]++;
                }
            });
        }
        return affinity(sums);
    }

    /** Ortalama puan 5 altındaysa 0; 10 ortalama 10000. Puan 1-10 tam sayıdır. */
    static Map<String, Integer> affinity(Map<String, long[]> sums) {
        Map<String, Integer> result = new LinkedHashMap<>();
        sums.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            long averageHundredths = entry.getValue()[0] * 100 / entry.getValue()[1];
            long value = Math.min(10000, Math.max(0, (averageHundredths - 500) * 10000 / 500));
            if (value > 0) result.put(entry.getKey(), (int) value);
        });
        return Map.copyOf(result);
    }

    private Map<UUID, Integer> ratings(Actor actor) {
        Map<UUID, Integer> ratings = new LinkedHashMap<>();
        String cursor = null;
        for (int page = 0; page < MAX_PAGES; page++) {
            var uri = UriComponentsBuilder.fromUri(RecommendationHttpClient.origin(reviewUrl))
                    .path("/api/v1/review/ratings/mine").queryParam("size", PAGE_SIZE);
            if (cursor != null) uri.queryParam("after", cursor);
            URI target = uri.build().encode().toUri();
            JsonNode reply = http.get(target, actor.bearer());
            cursor = readPage(reply, ratings);
            if (cursor == null) break;
        }
        return ratings;
    }

    /** Sayfayı doğrular ve birikime ekler; sonraki imleci (yoksa null) döner. */
    static String readPage(JsonNode reply, Map<UUID, Integer> into) {
        JsonNode items = reply.path("items");
        JsonNode next = reply.path("nextCursor");
        if (!items.isArray() || items.size() > PAGE_SIZE || !reply.path("truncated").isBoolean()
                || !(next.isNull() || next.isMissingNode() || next.isString())) throw RecommendationHttpClient.unavailable();
        for (JsonNode item : items) {
            UUID id = uuid(item.path("gameId"));
            JsonNode rating = item.path("rating");
            if (!rating.isIntegralNumber() || !rating.canConvertToInt() || rating.asInt() < 1 || rating.asInt() > 10
                    || into.putIfAbsent(id, rating.asInt()) != null) throw RecommendationHttpClient.unavailable();
        }
        if (!next.isString()) return null;
        String cursor = next.asString();
        if (cursor.isBlank() || cursor.length() > MAX_CURSOR || items.isEmpty()) throw RecommendationHttpClient.unavailable();
        return cursor;
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

package com.ltz.recommendation.infrastructure.client;

import com.ltz.recommendation.application.port.GameMetadataSource;
import com.ltz.recommendation.application.port.LibraryTasteSource;
import com.ltz.recommendation.application.port.PersonalizationSource.Actor;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

/** Library recommendation-signals (servis token'ı) + Game GAME-15 tür kodları; kaynak hatasında fail-closed. */
@Component
public class HttpLibraryTasteSource implements LibraryTasteSource {
    private static final int PAGE_SIZE = 50;
    private static final int MAX_PAGES = 10;
    private static final int GAME_BATCH = 100;
    private final RecommendationHttpClient http;
    private final RecommendationServiceTokenClient tokens;
    private final GameMetadataSource games;
    private final String libraryUrl;

    public HttpLibraryTasteSource(RecommendationHttpClient http, RecommendationServiceTokenClient tokens,
            GameMetadataSource games, @Value("${LIBRARY_SERVICE_URL:}") String libraryUrl) {
        this.http = http;
        this.tokens = tokens;
        this.games = games;
        this.libraryUrl = libraryUrl;
    }

    @Override
    public Affinity affinity(Actor actor) {
        String bearer = tokens.libraryToken(actor.accountId());
        Set<UUID> favorites = new LinkedHashSet<>();
        Set<UUID> completed = new LinkedHashSet<>();
        Set<UUID> seen = new HashSet<>();
        long total = Long.MAX_VALUE;
        for (int page = 0; page < MAX_PAGES && page * (long) PAGE_SIZE < total; page++) {
            URI uri = UriComponentsBuilder.fromUri(RecommendationHttpClient.origin(libraryUrl))
                    .path("/internal/v1/library/users/{id}/recommendation-signals")
                    .queryParam("page", page).queryParam("size", PAGE_SIZE).buildAndExpand(actor.accountId()).toUri();
            JsonNode reply = http.get(uri, bearer);
            total = HttpPersonalizationSource.validateLibraryPage(reply, page, total);
            collect(reply.path("items"), seen, favorites, completed);
        }
        Set<UUID> all = new LinkedHashSet<>(favorites);
        all.addAll(completed);
        if (all.isEmpty()) return Affinity.NONE;
        Map<UUID, List<String>> genres = genres(new ArrayList<>(all));
        return new Affinity(share(favorites, genres), share(completed, genres));
    }

    private static void collect(JsonNode items, Set<UUID> seen, Set<UUID> favorites, Set<UUID> completed) {
        for (JsonNode item : items) {
            if (!item.path("favorite").isBoolean() || !item.path("completed").isBoolean()) throw RecommendationHttpClient.unavailable();
            UUID id = HttpPersonalizationSource.uuid(item.path("gameId"));
            if (!seen.add(id)) throw RecommendationHttpClient.unavailable();
            if (item.path("favorite").asBoolean()) favorites.add(id);
            if (item.path("completed").asBoolean()) completed.add(id);
        }
    }

    private Map<UUID, List<String>> genres(List<UUID> ids) {
        Map<UUID, List<String>> found = new HashMap<>();
        for (int from = 0; from < ids.size(); from += GAME_BATCH) {
            found.putAll(games.genres(List.copyOf(ids.subList(from, Math.min(ids.size(), from + GAME_BATCH)))));
        }
        return found;
    }

    /**
     * Kümedeki görünür (türü çözülen) oyunların her türü taşıma payı; 10000 tüm oyunların o türde olduğu anlamına gelir.
     * Görünmeyen/yetişkin oyun paydaya girmez. Hiç oyun çözülmezse boş harita döner.
     */
    static Map<String, Integer> share(Set<UUID> members, Map<UUID, List<String>> genres) {
        Map<String, Integer> counts = new TreeMap<>();
        int resolved = 0;
        for (UUID id : members) {
            List<String> codes = genres.get(id);
            if (codes == null) continue;
            resolved++;
            for (String code : codes.stream().map(value -> value.toLowerCase(Locale.ROOT)).distinct().toList()) {
                counts.merge(code, 1, Integer::sum);
            }
        }
        Map<String, Integer> result = new TreeMap<>();
        if (resolved == 0) return result;
        for (var entry : counts.entrySet()) result.put(entry.getKey(), entry.getValue() * 10000 / resolved);
        return result;
    }
}

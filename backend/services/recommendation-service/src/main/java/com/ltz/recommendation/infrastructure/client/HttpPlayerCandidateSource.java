package com.ltz.recommendation.infrastructure.client;

import com.ltz.recommendation.application.port.PersonalizationSource.Actor;
import com.ltz.recommendation.application.port.PlayerCandidateSource;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

/** User'ın {@code recommendation-candidates} ucundan, hesaba bağlı {@code candidates:read} servis token'ıyla okur. */
@Component
public class HttpPlayerCandidateSource implements PlayerCandidateSource {
    static final int PAGE_SIZE = 50;
    static final int MAX_PAGES = 4;

    private final RecommendationHttpClient http;
    private final RecommendationServiceTokenClient tokens;
    private final String userUrl;

    public HttpPlayerCandidateSource(RecommendationHttpClient http, RecommendationServiceTokenClient tokens,
            @Value("${USER_SERVICE_URL:}") String userUrl) {
        this.http = http;
        this.tokens = tokens;
        this.userUrl = userUrl;
    }

    @Override
    public Taste viewer(Actor actor) {
        JsonNode profile = http.get(RecommendationHttpClient.origin(userUrl).resolve("/api/v1/user/me"), actor.bearer());
        if (!actor.accountId().equals(uuid(profile.path("userId")))) throw RecommendationHttpClient.unavailable();
        return new Taste(strings(profile.path("favoriteGenres")), strings(profile.path("favoritePlatforms")),
                strings(profile.path("playerTypes")));
    }

    @Override
    public Scan candidates(Actor actor, boolean excludeFriends, String genre, String platform) {
        String bearer = tokens.candidatesToken(actor.accountId());
        List<Candidate> found = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        String cursor = null;
        for (int page = 0; page < MAX_PAGES; page++) {
            var uri = UriComponentsBuilder.fromUri(RecommendationHttpClient.origin(userUrl))
                    .path("/internal/v1/accounts/{id}/recommendation-candidates")
                    .queryParam("limit", PAGE_SIZE).queryParam("excludeFriends", excludeFriends);
            if (genre != null) uri.queryParam("genre", genre);
            if (platform != null) uri.queryParam("platform", platform);
            if (cursor != null) uri.queryParam("cursor", cursor);
            URI target = uri.buildAndExpand(actor.accountId()).encode().toUri();
            JsonNode reply = http.get(target, bearer);
            JsonNode items = reply.path("candidates");
            if (!items.isArray() || items.size() > PAGE_SIZE) throw RecommendationHttpClient.unavailable();
            for (JsonNode item : items) {
                UUID id = uuid(item.path("userId"));
                // Kendisi veya tekrar eden aday, kaynağın sözleşme ihlalidir; sessizce düzeltilmez.
                if (id.equals(actor.accountId()) || !seen.add(id)) throw RecommendationHttpClient.unavailable();
                found.add(new Candidate(id, new Taste(strings(item.path("favoriteGenres")),
                        strings(item.path("favoritePlatforms")), strings(item.path("playerTypes")))));
            }
            JsonNode next = reply.path("nextCursor");
            if (next.isNull() || next.isMissingNode()) return new Scan(found, false);
            cursor = uuid(next).toString();
        }
        return new Scan(found, true);
    }

    private static UUID uuid(JsonNode value) {
        if (!value.isString()) throw RecommendationHttpClient.unavailable();
        try {
            UUID id = UUID.fromString(value.asString());
            if (!id.toString().equalsIgnoreCase(value.asString())) throw RecommendationHttpClient.unavailable();
            return id;
        } catch (IllegalArgumentException _) { throw RecommendationHttpClient.unavailable(); }
    }

    private static Set<String> strings(JsonNode value) {
        if (!value.isArray() || value.size() > 10) throw RecommendationHttpClient.unavailable();
        Set<String> values = new TreeSet<>();
        for (var item : value) {
            if (!item.isString() || item.asString().isBlank() || item.asString().length() > 64
                    || item.asString().chars().anyMatch(Character::isISOControl)) throw RecommendationHttpClient.unavailable();
            values.add(item.asString());
        }
        return values;
    }
}

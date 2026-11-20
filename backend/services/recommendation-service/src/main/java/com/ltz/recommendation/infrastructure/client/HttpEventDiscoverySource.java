package com.ltz.recommendation.infrastructure.client;

import com.ltz.recommendation.application.port.EventDiscoverySource;
import com.ltz.recommendation.application.port.PersonalizationSource.Actor;
import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeParseException;
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

/**
 * Event {@code GET /api/v1/event/events} (EVT-04): kullanıcı token'ıyla, yaklaşana göre artan sırada, en çok
 * {@value #MAX_PAGES} sayfa. Yanıt sıkı doğrulanır; sözleşme ihlali 503 olur (sessizce düzeltilmez).
 */
@Component
public class HttpEventDiscoverySource implements EventDiscoverySource {
    static final int PAGE_SIZE = 50;
    static final int MAX_PAGES = 4;

    private final RecommendationHttpClient http;
    private final String eventUrl;

    public HttpEventDiscoverySource(RecommendationHttpClient http, @Value("${EVENT_SERVICE_URL:}") String eventUrl) {
        this.http = http;
        this.eventUrl = eventUrl;
    }

    @Override
    public Scan upcoming(Actor actor) {
        List<Event> found = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (int page = 0; page < MAX_PAGES; page++) {
            URI target = UriComponentsBuilder.fromUri(RecommendationHttpClient.origin(eventUrl))
                    .path("/api/v1/event/events").queryParam("page", page).queryParam("size", PAGE_SIZE)
                    .build().toUri();
            JsonNode reply = http.get(target, actor.bearer());
            JsonNode items = reply.path("items");
            if (!items.isArray() || items.size() > PAGE_SIZE || !reply.path("hasNext").isBoolean()
                    || reply.path("page").asInt(-1) != page) throw RecommendationHttpClient.unavailable();
            for (JsonNode item : items) {
                Event event = event(item);
                if (!seen.add(event.id())) throw RecommendationHttpClient.unavailable();
                found.add(event);
            }
            if (!reply.path("hasNext").asBoolean()) return new Scan(found, false);
        }
        return new Scan(found, true);
    }

    private static Event event(JsonNode item) {
        try {
            return new Event(uuid(item.path("id")), optionalUuid(item.path("organizerId")), text(item.path("type"), 32),
                    optionalUuid(item.path("gameId")), platforms(item.path("platforms")), text(item.path("title"), 200),
                    instant(item.path("startsAt")), instant(item.path("endsAt")), text(item.path("status"), 32),
                    optionalCount(item.path("capacity"), 1), count(item.path("participantCount")),
                    viewerParticipation(item.path("viewerParticipation")), bool(item.path("joinable")));
        } catch (IllegalArgumentException _) { throw RecommendationHttpClient.unavailable(); }
    }

    private static boolean bool(JsonNode value) {
        if (!value.isBoolean()) throw RecommendationHttpClient.unavailable();
        return value.asBoolean();
    }

    private static int count(JsonNode value) {
        if (!value.isInt() || value.asInt() < 0) throw RecommendationHttpClient.unavailable();
        return value.asInt();
    }

    private static Integer optionalCount(JsonNode value, int min) {
        if (value.isNull()) return null;
        if (!value.isInt() || value.asInt() < min) throw RecommendationHttpClient.unavailable();
        return value.asInt();
    }

    private static String viewerParticipation(JsonNode value) {
        return value.isNull() ? null : text(value, 32);
    }

    private static UUID optionalUuid(JsonNode value) {
        return value.isNull() || value.isMissingNode() ? null : uuid(value);
    }

    private static UUID uuid(JsonNode value) {
        if (!value.isString()) throw RecommendationHttpClient.unavailable();
        UUID id = UUID.fromString(value.asString());
        if (!id.toString().equalsIgnoreCase(value.asString())) throw RecommendationHttpClient.unavailable();
        return id;
    }

    private static Instant instant(JsonNode value) {
        if (!value.isString()) throw RecommendationHttpClient.unavailable();
        try { return Instant.parse(value.asString()); } catch (DateTimeParseException _) { throw RecommendationHttpClient.unavailable(); }
    }

    private static String text(JsonNode value, int max) {
        if (!value.isString() || value.asString().isBlank() || value.asString().length() > max) throw RecommendationHttpClient.unavailable();
        return value.asString();
    }

    private static Set<String> platforms(JsonNode value) {
        if (!value.isArray() || value.size() > 6) throw RecommendationHttpClient.unavailable();
        Set<String> values = new TreeSet<>();
        for (JsonNode item : value) values.add(text(item, 32));
        return values;
    }
}

package com.ltz.performance.infrastructure.client;

import com.ltz.performance.application.port.GameCatalog;
import com.ltz.performance.domain.exception.ServiceDependencyUnavailableException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Game Service {@code POST /api/v1/game/games/batch} (GAME-15, anonim). Tek kimlik sorulur; çağrı transaction
 * dışında yapılır ve otomatik yeniden deneme yoktur (429 için sıkı döngü yasaktır). 200 dışındaki her yanıt,
 * zaman aşımı ve bozuk gövde kapalı başarısızlık olarak 503'e gider.
 */
@Component
public class GameServiceCatalog implements GameCatalog {
    private static final String UNAVAILABLE = ServiceDependencyUnavailableException.GAME_SERVICE_UNAVAILABLE;

    private final BoundedJsonClient client;
    private final PerformanceClientProperties properties;
    private final ObjectMapper mapper;

    public GameServiceCatalog(PerformanceClientProperties properties, ObjectMapper mapper) {
        this.client = new BoundedJsonClient(properties.connectTimeout(), properties.readTimeout());
        this.properties = properties;
        this.mapper = mapper;
    }

    @Override
    public Optional<CatalogGame> find(UUID gameId) {
        try {
            var reply = client.post(properties.gamesBatchUri(), Map.of("gameIds", List.of(gameId)), null);
            if (reply.status() != 200) throw new ServiceDependencyUnavailableException(UNAVAILABLE);
            return parse(mapper.readTree(reply.body()), gameId);
        } catch (RestClientException | JacksonException | IllegalArgumentException _) {
            throw new ServiceDependencyUnavailableException(UNAVAILABLE);
        }
    }

    private static Optional<CatalogGame> parse(JsonNode root, UUID gameId) {
        JsonNode items = array(root, "items");
        JsonNode unavailable = array(root, "unavailable");
        for (JsonNode item : items) {
            if (isGame(item.get("gameId"), gameId)) return Optional.of(new CatalogGame(gameId, title(item.get("title"))));
        }
        for (JsonNode id : unavailable) {
            if (isGame(id, gameId)) return Optional.empty();
        }
        // Ne kart ne de "unavailable" listesinde: sözleşme dışı yanıt, "yok" diye yorumlanmaz.
        throw new ServiceDependencyUnavailableException(UNAVAILABLE);
    }

    private static JsonNode array(JsonNode root, String field) {
        JsonNode node = root == null || !root.isObject() ? null : root.get(field);
        if (node == null || !node.isArray()) throw new ServiceDependencyUnavailableException(UNAVAILABLE);
        return node;
    }

    private static boolean isGame(JsonNode id, UUID gameId) {
        return id != null && id.isString() && gameId.equals(UUID.fromString(id.asString()));
    }

    private static String title(JsonNode title) {
        if (title == null || !title.isString() || title.asString().isBlank()) {
            throw new ServiceDependencyUnavailableException(UNAVAILABLE);
        }
        return title.asString();
    }
}

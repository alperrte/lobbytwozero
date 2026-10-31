package com.ltz.library.infrastructure.client;

import com.ltz.library.application.port.GameCatalogPort;
import com.ltz.library.application.port.GameSummary;
import com.ltz.library.domain.exception.CatalogUnavailableException;
import com.ltz.library.domain.exception.GameUnavailableException;
import com.ltz.library.infrastructure.client.dto.request.GameBatchRequest;
import com.ltz.library.infrastructure.client.dto.response.GameBatchResponse;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.Set;
import java.net.URI;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Public GAME-15 sözleşmesi; kullanıcı token'ı dış servise aktarılmaz, retry yapılmaz. */
@Component
public class GameServiceClient implements GameCatalogPort {
    private final RestClient client;

    public GameServiceClient(RestClient.Builder builder, @Value("${GAME_SERVICE_URL:http://game-service:8080}") String baseUrl) {
        client = builder.baseUrl(baseUrl).build();
    }

    @Override
    public void requireAvailable(UUID gameId) {
        if (!summaries(List.of(gameId)).containsKey(gameId)) throw new GameUnavailableException();
    }

    @Override
    public Map<UUID, GameSummary> summaries(List<UUID> gameIds) {
        if (gameIds.isEmpty()) return Map.of();
        if (gameIds.size() > 50 || gameIds.stream().anyMatch(java.util.Objects::isNull))
            throw new IllegalArgumentException("Game batch sınırı 1–50 kimliktir.");
        var requested = new LinkedHashSet<>(gameIds);
        return validateResponse(fetch(List.copyOf(requested)), requested);
    }

    private GameBatchResponse fetch(List<UUID> gameIds) {
        GameBatchResponse response;
        try {
            var result = client.post().uri("/api/v1/game/games/batch").contentType(MediaType.APPLICATION_JSON)
                    .body(new GameBatchRequest(gameIds)).retrieve().toEntity(GameBatchResponse.class);
            if (result.getStatusCode().value() != 200) throw new CatalogUnavailableException();
            response = result.getBody();
        } catch (RestClientException error) {
            throw new CatalogUnavailableException(error);
        }
        return response;
    }

    private static Map<UUID, GameSummary> validateResponse(GameBatchResponse response, Set<UUID> requested) {
        requireEnvelope(response);
        Set<UUID> seen = new HashSet<>();
        Map<UUID, GameSummary> summaries = new LinkedHashMap<>();
        for (var card : response.items()) {
            if (card == null || !requested.contains(card.gameId()) || !seen.add(card.gameId())) throw new CatalogUnavailableException();
            summaries.put(card.gameId(), new GameSummary(card.gameId(), title(card.title()), cover(card.coverUrl()),
                    known(card.origin(), Set.of("INTERNAL", "EXTERNAL")),
                    known(card.metadataCompleteness(), Set.of("INDEX_ONLY", "PARTIAL", "FULL")),
                    known(card.gameType(), Set.of("MAIN_GAME", "DLC", "DEMO", "SOUNDTRACK", "SOFTWARE", "VIDEO", "PACKAGE", "HARDWARE", "UNKNOWN"))));
        }
        for (UUID id : response.unavailable())
            if (!requested.contains(id) || !seen.add(id)) throw new CatalogUnavailableException();
        if (!seen.equals(requested)) throw new CatalogUnavailableException();
        return Map.copyOf(summaries);
    }

    private static void requireEnvelope(GameBatchResponse response) {
        if (response == null || response.items() == null || response.unavailable() == null)
            throw new CatalogUnavailableException();
    }

    private static String known(String value, Set<String> allowed) { return value != null && allowed.contains(value) ? value : null; }

    private static String title(String value) { return value == null || value.isBlank() || value.length() > 256 ? null : value.strip(); }

    private static String cover(String value) {
        if (value == null || value.length() > 2048) return null;
        try {
            URI uri = URI.create(value);
            return "https".equals(uri.getScheme()) && uri.getHost() != null && uri.getRawUserInfo() == null ? value : null;
        } catch (IllegalArgumentException _) { return null; }
    }
}

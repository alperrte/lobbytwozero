package com.ltz.library.infrastructure.client;

import com.ltz.library.application.port.GameCatalogPort;
import com.ltz.library.domain.exception.CatalogUnavailableException;
import com.ltz.library.domain.exception.GameUnavailableException;
import com.ltz.library.infrastructure.client.dto.request.GameBatchRequest;
import com.ltz.library.infrastructure.client.dto.response.GameBatchResponse;
import java.util.List;
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
        GameBatchResponse response;
        try {
            var result = client.post().uri("/api/v1/game/games/batch").contentType(MediaType.APPLICATION_JSON)
                    .body(new GameBatchRequest(List.of(gameId))).retrieve().toEntity(GameBatchResponse.class);
            if (result.getStatusCode().value() != 200) throw new CatalogUnavailableException();
            response = result.getBody();
        } catch (RestClientException error) {
            throw new CatalogUnavailableException(error);
        }
        validateResponse(response, gameId);
    }

    private static void validateResponse(GameBatchResponse response, UUID gameId) {
        if (response == null || response.items() == null || response.unavailable() == null)
            throw new CatalogUnavailableException();
        if (response.items().isEmpty() && response.unavailable().equals(List.of(gameId)))
            throw new GameUnavailableException();
        if (response.items().size() != 1 || response.items().getFirst() == null
                || !gameId.equals(response.items().getFirst().gameId()) || !response.unavailable().isEmpty())
            throw new CatalogUnavailableException();
    }
}

package com.ltz.review.infrastructure.client;

import com.ltz.review.application.port.GameCatalogPort;
import com.ltz.review.domain.exception.ReviewException;
import com.ltz.review.infrastructure.client.dto.response.GameBatchResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class GameServiceClient implements GameCatalogPort {
    private final RestClient client;

    public GameServiceClient(RestClient.Builder builder, @Value("${GAME_SERVICE_URL:http://game-service:8080}") String url) {
        client = builder.baseUrl(url).build();
    }

    @Override
    public void requireAvailable(UUID gameId) {
        GameBatchResponse result;
        try {
            result = client.post().uri("/api/v1/game/games/batch").contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("gameIds", List.of(gameId))).retrieve().body(GameBatchResponse.class);
        } catch (RestClientException exception) {
            throw new ReviewException(503, "Oyun doğrulaması geçici olarak kullanılamıyor.", exception);
        }
        validate(result, gameId);
    }

    private static void validate(GameBatchResponse result, UUID gameId) {
        if (result == null || result.items() == null || result.unavailable() == null)
            throw new ReviewException(503, "Oyun doğrulama yanıtı geçersiz.");
        if (result.items().size() == 1 && result.items().getFirst() != null
                && gameId.equals(result.items().getFirst().gameId()) && result.unavailable().isEmpty()) return;
        if (result.items().isEmpty() && result.unavailable().equals(List.of(gameId)))
            throw new ReviewException(404, "Oyun bulunamadı veya kullanılamıyor.");
        throw new ReviewException(503, "Oyun doğrulama yanıtı geçersiz.");
    }
}

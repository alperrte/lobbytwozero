package com.ltz.community.infrastructure.client;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.ltz.community.application.port.GameCatalogPort;
import com.ltz.community.domain.exception.GameCatalogUnavailableException;
import com.ltz.community.infrastructure.client.dto.request.GameBatchRequest;
import com.ltz.community.infrastructure.client.dto.response.GameBatchResponse;

/**
 * game-service toplu kart ucuyla ({@code POST /api/v1/game/games/batch}, anonim) tek kimlik doğrular.
 * Zaman aşımı/yönlendirme ayarları {@code spring.http.clients.*} ile gelir; hata halinde yeniden deneme yapılmaz.
 */
@Component
public class GameServiceClient implements GameCatalogPort {

	private final RestClient restClient;
	private final String batchPath;

	public GameServiceClient(RestClient.Builder builder, @Value("${ltz.clients.game-service.url}") String baseUrl,
			@Value("${ltz.clients.game-service.batch-path:/api/v1/game/games/batch}") String batchPath) {
		this.restClient = builder.baseUrl(baseUrl).build();
		this.batchPath = batchPath;
	}

	@Override
	public boolean isAvailable(UUID gameId) {
		GameBatchResponse response;
		try {
			response = restClient.post()
					.uri(batchPath)
					.contentType(MediaType.APPLICATION_JSON)
					.body(new GameBatchRequest(List.of(gameId)))
					.retrieve()
					.body(GameBatchResponse.class);
		} catch (RestClientException exception) {
			throw new GameCatalogUnavailableException(exception);
		}
		if (response == null || response.items() == null) {
			throw new GameCatalogUnavailableException(new IllegalStateException("Empty game catalog response"));
		}
		return response.items().stream().anyMatch(item -> gameId.equals(item.gameId()));
	}
}

package com.ltz.community.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;

import com.ltz.community.domain.exception.GameCatalogUnavailableException;

@RestClientTest(GameServiceClient.class)
@TestPropertySource(properties = "ltz.clients.game-service.url=http://game-service:8080")
class GameServiceClientTest {

	private static final UUID GAME_ID = UUID.fromString("0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66");
	private static final String BATCH_URL = "http://game-service:8080/api/v1/game/games/batch";

	@Autowired
	private GameServiceClient client;

	@Autowired
	private MockRestServiceServer server;

	@Test
	void gameListedInItemsIsAvailableAndRequestUsesBatchContract() {
		server.expect(requestTo(BATCH_URL))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
				.andExpect(jsonPath("$.gameIds.length()").value(1))
				.andExpect(jsonPath("$.gameIds[0]").value(GAME_ID.toString()))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andRespond(withSuccess("{\"items\":[{\"gameId\":\"" + GAME_ID
						+ "\",\"title\":\"Örnek\",\"origin\":\"INTERNAL\",\"metadataCompleteness\":\"FULL\","
						+ "\"gameType\":\"MAIN_GAME\",\"externalReferences\":[],\"coverUrl\":null,"
						+ "\"fieldAddedLater\":1}],\"unavailable\":[]}", MediaType.APPLICATION_JSON));

		assertThat(client.isAvailable(GAME_ID)).isTrue();
		server.verify();
	}

	@Test
	void gameListedAsUnavailableIsNotAvailable() {
		server.expect(requestTo(BATCH_URL))
				.andRespond(withSuccess("{\"items\":[],\"unavailable\":[\"" + GAME_ID + "\"]}",
						MediaType.APPLICATION_JSON));

		assertThat(client.isAvailable(GAME_ID)).isFalse();
	}

	@Test
	void serverErrorMeansCatalogUnavailable() {
		server.expect(requestTo(BATCH_URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

		assertThatThrownBy(() -> client.isAvailable(GAME_ID)).isInstanceOf(GameCatalogUnavailableException.class);
	}

	@Test
	void rateLimitMeansCatalogUnavailableWithoutRetry() {
		server.expect(requestTo(BATCH_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

		assertThatThrownBy(() -> client.isAvailable(GAME_ID)).isInstanceOf(GameCatalogUnavailableException.class);
		server.verify();
	}

	@Test
	void connectionFailureMeansCatalogUnavailable() {
		server.expect(requestTo(BATCH_URL)).andRespond(withException(new IOException("connection refused")));

		assertThatThrownBy(() -> client.isAvailable(GAME_ID)).isInstanceOf(GameCatalogUnavailableException.class);
	}

	@Test
	void malformedOrEmptyResponseMeansCatalogUnavailable() {
		server.expect(requestTo(BATCH_URL)).andRespond(withSuccess("not json", MediaType.APPLICATION_JSON));
		server.expect(requestTo(BATCH_URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> client.isAvailable(GAME_ID)).isInstanceOf(GameCatalogUnavailableException.class);
		assertThatThrownBy(() -> client.isAvailable(GAME_ID)).isInstanceOf(GameCatalogUnavailableException.class);
	}
}

package com.ltz.content.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.ltz.content.domain.exception.GameCatalogUnavailableException;

class GameServiceClientTest {

	private static final UUID GAME_ID = UUID.fromString("0198a3c2-aaaa-7d44-9a55-3f1c2b4d5e66");
	private static final String URL = "http://game-service:8080/api/v1/game/games/batch";

	private MockRestServiceServer server;
	private GameServiceClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		client = new GameServiceClient(builder, "http://game-service:8080", "/api/v1/game/games/batch");
	}

	@Test
	void aGameInTheBatchResponseIsAvailable() {
		server.expect(requestTo(URL))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.gameIds[0]").value(GAME_ID.toString()))
				.andRespond(withSuccess("{\"items\":[{\"gameId\":\"" + GAME_ID + "\",\"title\":\"Oyun\",\"yeniAlan\":1}],\"unavailable\":[]}",
						MediaType.APPLICATION_JSON));

		assertThat(client.isAvailable(GAME_ID)).isTrue();
		server.verify();
	}

	@Test
	void aGameListedAsUnavailableIsNotAvailable() {
		server.expect(requestTo(URL)).andRespond(withSuccess("{\"items\":[],\"unavailable\":[\"" + GAME_ID + "\"]}",
				MediaType.APPLICATION_JSON));

		assertThat(client.isAvailable(GAME_ID)).isFalse();
	}

	@Test
	void anotherGameIdInTheResponseDoesNotCount() {
		server.expect(requestTo(URL)).andRespond(withSuccess("{\"items\":[{\"gameId\":\"" + UUID.randomUUID() + "\"}]}",
				MediaType.APPLICATION_JSON));

		assertThat(client.isAvailable(GAME_ID)).isFalse();
	}

	@Test
	void errorsAndEmptyResponsesMeanTheCatalogIsUnavailable() {
		server.expect(requestTo(URL)).andRespond(withServerError());
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.OK));
		server.expect(requestTo(URL)).andRespond(withSuccess("{\"unavailable\":[]}", MediaType.APPLICATION_JSON));

		for (int attempt = 0; attempt < 4; attempt++) {
			assertThatThrownBy(() -> client.isAvailable(GAME_ID)).isInstanceOf(GameCatalogUnavailableException.class);
		}
		server.verify();
	}
}

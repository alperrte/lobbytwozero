package com.ltz.event.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;

import com.ltz.event.domain.exception.GameCatalogUnavailableException;
import com.ltz.event.domain.exception.GameSearchTooBroadException;

/** Game Service sözleşmesi (docs/api.md GAME-03 / GAME-15): herkese açık uçlar, önek araması, sayfa başına en çok 100. */
@RestClientTest(GameServiceClient.class)
@TestPropertySource(properties = "ltz.clients.game-service.url=http://game-service:8080")
class GameServiceClientTest {

	private static final UUID GAME_ID = UUID.fromString("0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66");
	private static final String BATCH_URL = "http://game-service:8080/api/v1/game/games/batch";

	@Autowired
	private GameServiceClient client;

	@Autowired
	private MockRestServiceServer server;

	private static String search(int page) {
		return "http://game-service:8080/api/v1/game/games/search?q=elden&page=" + page + "&size=100";
	}

	private static List<UUID> ids(int from, int count) {
		return IntStream.range(from, from + count).mapToObj(i -> new UUID(0, i + 1L)).toList();
	}

	/** Game Service'in GameDiscoveryResponse biçimi: kart alanları ve ek alanlar yok sayılmalıdır. */
	private static String searchPage(int page, long total, List<UUID> ids) {
		String items = ids.stream()
				.map(id -> "{\"game\":{\"gameId\":\"" + id + "\",\"title\":\"X\",\"origin\":\"INTERNAL\","
						+ "\"externalReferences\":[],\"coverUrl\":null},\"rating\":{\"status\":\"UNRATED\"},\"extra\":1}")
				.collect(Collectors.joining(","));
		return "{\"items\":[" + items + "],\"page\":" + page + ",\"size\":100,\"totalElements\":" + total
				+ ",\"sort\":\"TITLE\",\"direction\":\"ASC\",\"ratingsSyncedAt\":null}";
	}

	private void expectSearchPage(String query, int page, long total, List<UUID> ids) {
		server.expect(requestTo("http://game-service:8080/api/v1/game/games/search?q=" + query + "&page=" + page
				+ "&size=100"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(searchPage(page, total, ids), MediaType.APPLICATION_JSON));
	}

	// ------------------------------------------------------------------ isAvailable (EVT-17)

	@Test
	void gameListedInItemsIsAvailableAndRequestUsesTheBatchContract() {
		server.expect(requestTo(BATCH_URL))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
				.andExpect(headerDoesNotExist("Authorization"))
				.andExpect(jsonPath("$.gameIds.length()").value(1))
				.andExpect(jsonPath("$.gameIds[0]").value(GAME_ID.toString()))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andRespond(withSuccess("{\"items\":[{\"gameId\":\"" + GAME_ID + "\",\"title\":\"Örnek\","
						+ "\"gameType\":\"MAIN_GAME\",\"fieldAddedLater\":1}],\"unavailable\":[]}",
						MediaType.APPLICATION_JSON));

		assertThat(client.isAvailable(GAME_ID)).isTrue();
		server.verify();
	}

	@Test
	void gameListedAsUnavailableIsNotAvailable() {
		server.expect(requestTo(BATCH_URL))
				.andRespond(withSuccess("{\"items\":[],\"unavailable\":[\"" + GAME_ID + "\"]}", MediaType.APPLICATION_JSON));

		assertThat(client.isAvailable(GAME_ID)).isFalse();
	}

	@Test
	void anotherGameInTheItemsDoesNotMakeThisOneAvailable() {
		server.expect(requestTo(BATCH_URL))
				.andRespond(withSuccess("{\"items\":[{\"gameId\":\"" + UUID.randomUUID() + "\"}],\"unavailable\":[]}",
						MediaType.APPLICATION_JSON));

		assertThat(client.isAvailable(GAME_ID)).isFalse();
	}

	@Test
	void batchFailuresMeanCatalogUnavailableNeverMissingGame() {
		server.expect(requestTo(BATCH_URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
		assertThatThrownBy(() -> client.isAvailable(GAME_ID)).isInstanceOf(GameCatalogUnavailableException.class);

		server.reset();
		server.expect(requestTo(BATCH_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
		assertThatThrownBy(() -> client.isAvailable(GAME_ID)).isInstanceOf(GameCatalogUnavailableException.class);

		server.reset();
		server.expect(requestTo(BATCH_URL)).andRespond(withException(new IOException("zaman aşımı")));
		assertThatThrownBy(() -> client.isAvailable(GAME_ID)).isInstanceOf(GameCatalogUnavailableException.class);

		server.reset();
		server.expect(requestTo(BATCH_URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
		assertThatThrownBy(() -> client.isAvailable(GAME_ID)).isInstanceOf(GameCatalogUnavailableException.class);
	}

	// ------------------------------------------------------------------ findGameIdsByNamePrefix (EVT-05)

	@Test
	void singlePageSearchReturnsAllGameIdsAndUsesThePublicSearchContract() {
		server.expect(requestTo(search(0)))
				.andExpect(method(HttpMethod.GET))
				.andExpect(headerDoesNotExist("Authorization"))
				.andRespond(withSuccess(searchPage(0, 3, ids(0, 3)), MediaType.APPLICATION_JSON));

		Set<UUID> result = client.findGameIdsByNamePrefix("elden");

		assertThat(result).containsExactlyInAnyOrderElementsOf(ids(0, 3));
		server.verify();
	}

	@Test
	void everyPageIsReadSoTheResultIsComplete() {
		expectSearchPage("elden", 0, 250, ids(0, 100));
		expectSearchPage("elden", 1, 250, ids(100, 100));
		expectSearchPage("elden", 2, 250, ids(200, 50));

		Set<UUID> result = client.findGameIdsByNamePrefix("elden");

		assertThat(result).hasSize(250).containsExactlyInAnyOrderElementsOf(ids(0, 250));
		server.verify();
	}

	@Test
	void noMatchingGameReturnsAnEmptySetAfterOneCall() {
		expectSearchPage("elden", 0, 0, List.of());

		assertThat(client.findGameIdsByNamePrefix("elden")).isEmpty();
		server.verify();
	}

	@Test
	void exactlyTheMaximumNumberOfGamesIsStillSearchable() {
		for (int page = 0; page < 10; page++) {
			expectSearchPage("elden", page, 1000, ids(page * 100, 100));
		}

		assertThat(client.findGameIdsByNamePrefix("elden")).hasSize(1000);
		server.verify();
	}

	@Test
	void moreMatchingGamesThanTheMaximumIsRejectedAfterTheFirstCallWithoutTruncating() {
		expectSearchPage("a", 0, 1001, ids(0, 100));

		assertThatThrownBy(() -> client.findGameIdsByNamePrefix("a"))
				.isInstanceOfSatisfying(GameSearchTooBroadException.class,
						exception -> assertThat(exception.getMaxGames()).isEqualTo(1000));
		server.verify();
	}

	@Test
	void anErrorOnALaterPageMeansUnavailableNotAPartialResult() {
		expectSearchPage("elden", 0, 250, ids(0, 100));
		server.expect(requestTo(search(1))).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

		assertThatThrownBy(() -> client.findGameIdsByNamePrefix("elden"))
				.isInstanceOf(GameCatalogUnavailableException.class);
	}

	@Test
	void aChangingTotalBetweenPagesMeansUnavailable() {
		expectSearchPage("elden", 0, 250, ids(0, 100));
		expectSearchPage("elden", 1, 251, ids(100, 100));

		assertThatThrownBy(() -> client.findGameIdsByNamePrefix("elden"))
				.isInstanceOf(GameCatalogUnavailableException.class);
	}

	@Test
	void anEmptyPageBeforeTheReportedTotalMeansUnavailable() {
		expectSearchPage("elden", 0, 150, ids(0, 100));
		expectSearchPage("elden", 1, 150, List.of());

		assertThatThrownBy(() -> client.findGameIdsByNamePrefix("elden"))
				.isInstanceOf(GameCatalogUnavailableException.class);
	}

	@Test
	void repeatedIdsAcrossPagesCannotFakeACompleteResult() {
		expectSearchPage("elden", 0, 150, ids(0, 100));
		expectSearchPage("elden", 1, 150, ids(0, 50));
		expectSearchPage("elden", 2, 150, List.of());

		assertThatThrownBy(() -> client.findGameIdsByNamePrefix("elden"))
				.isInstanceOf(GameCatalogUnavailableException.class);
	}

	@Test
	void invalidSearchResponsesMeanUnavailable() {
		server.expect(requestTo(search(0))).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
		assertThatThrownBy(() -> client.findGameIdsByNamePrefix("elden"))
				.isInstanceOf(GameCatalogUnavailableException.class);

		server.reset();
		server.expect(requestTo(search(0))).andRespond(withSuccess(
				"{\"items\":[{\"game\":{}}],\"page\":0,\"size\":100,\"totalElements\":1}", MediaType.APPLICATION_JSON));
		assertThatThrownBy(() -> client.findGameIdsByNamePrefix("elden"))
				.isInstanceOf(GameCatalogUnavailableException.class);

		server.reset();
		server.expect(requestTo(search(0))).andRespond(withSuccess(
				"{\"items\":[],\"page\":0,\"size\":100,\"totalElements\":-1}", MediaType.APPLICATION_JSON));
		assertThatThrownBy(() -> client.findGameIdsByNamePrefix("elden"))
				.isInstanceOf(GameCatalogUnavailableException.class);

		server.reset();
		server.expect(requestTo(search(0))).andRespond(withSuccess(searchPage(0, 101, ids(0, 101)),
				MediaType.APPLICATION_JSON));
		assertThatThrownBy(() -> client.findGameIdsByNamePrefix("elden"))
				.isInstanceOf(GameCatalogUnavailableException.class);
	}

	@Test
	void searchFailuresMeanCatalogUnavailable() {
		for (HttpStatus status : new HttpStatus[] { HttpStatus.BAD_REQUEST, HttpStatus.TOO_MANY_REQUESTS,
				HttpStatus.INTERNAL_SERVER_ERROR }) {
			server.reset();
			server.expect(requestTo(search(0))).andRespond(withStatus(status));

			assertThatThrownBy(() -> client.findGameIdsByNamePrefix("elden"))
					.isInstanceOf(GameCatalogUnavailableException.class);
		}
		server.reset();
		server.expect(requestTo(search(0))).andRespond(withException(new IOException("bağlantı kesildi")));
		assertThatThrownBy(() -> client.findGameIdsByNamePrefix("elden"))
				.isInstanceOf(GameCatalogUnavailableException.class);
	}

	@Test
	void searchTextIsSentAsAnEncodedQueryValue() {
		expectSearchPage("a%26b%20c%2Bd%C3%96", 0, 0, List.of());

		assertThat(client.findGameIdsByNamePrefix("a&b c+dÖ")).isEmpty();
		server.verify();
	}
}

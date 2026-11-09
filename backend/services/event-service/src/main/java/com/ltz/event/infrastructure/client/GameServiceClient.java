package com.ltz.event.infrastructure.client;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.ltz.event.application.port.GameCatalogPort;
import com.ltz.event.domain.exception.GameCatalogUnavailableException;
import com.ltz.event.domain.exception.GameSearchTooBroadException;
import com.ltz.event.infrastructure.client.dto.request.GameBatchRequest;
import com.ltz.event.infrastructure.client.dto.response.GameBatchResponse;
import com.ltz.event.infrastructure.client.dto.response.GameSearchResponse;

/**
 * Game Service'in <b>herkese açık</b> uçlarını kullanır (kimlik/servis token'ı gerekmez; gizli değer yoktur):
 * <ul>
 *   <li>{@code POST /api/v1/game/games/batch}: tek kimlikle oyunun yayında olduğunu doğrular (EVT-17).</li>
 *   <li>{@code GET /api/v1/game/games/search}: oyun adı <b>önek</b> araması (ACTIVE + MAIN_GAME), sayfa başına en çok
 *       100 sonuç (EVT-05). Tüm sayfalar okunur; toplam üst sınırı aşarsa kırpılmış sonuç yerine
 *       {@link GameSearchTooBroadException} fırlatılır.</li>
 * </ul>
 * Zaman aşımı/yönlendirme ayarları {@code spring.http.clients.*} ile gelir (yönlendirme izlenmez). Hata halinde yeniden
 * deneme yapılmaz; her başarısızlık {@link GameCatalogUnavailableException} olur ("oyun yok" ile karıştırılmaz). Yanıtlar
 * güvenilmez girdidir: yapı, toplam ve sayfa tutarlılığı doğrulanır. Oyun adı veya başka veri saklanmaz.
 */
@Component
public class GameServiceClient implements GameCatalogPort {

	/** Game Service'in izin verdiği en büyük sayfa boyutu (docs/api.md GAME-03: size 1–100). */
	static final int SEARCH_PAGE_SIZE = 100;

	private final RestClient restClient;
	private final String batchPath;
	private final String searchPath;
	private final int maxGames;

	public GameServiceClient(RestClient.Builder builder, @Value("${ltz.clients.game-service.url}") String baseUrl,
			@Value("${ltz.clients.game-service.batch-path:/api/v1/game/games/batch}") String batchPath,
			@Value("${ltz.clients.game-service.search-path:/api/v1/game/games/search}") String searchPath,
			@Value("${ltz.event.game-search.max-games:1000}") int maxGames) {
		this.restClient = builder.baseUrl(baseUrl).build();
		this.batchPath = batchPath;
		this.searchPath = searchPath;
		this.maxGames = maxGames;
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
			throw invalid("Empty game batch response");
		}
		return response.items().stream().anyMatch(item -> item != null && gameId.equals(item.gameId()));
	}

	@Override
	public Set<UUID> findGameIdsByNamePrefix(String text) {
		Set<UUID> ids = new LinkedHashSet<>();
		int maxPages = (maxGames + SEARCH_PAGE_SIZE - 1) / SEARCH_PAGE_SIZE + 1;
		long total = -1;
		for (int page = 0; page < maxPages && !isComplete(ids, total); page++) {
			GameSearchResponse response = fetchSearchPage(text, page);
			validate(response);
			total = checkTotal(total, response.totalElements());
			collect(response, ids);
			if (response.items().isEmpty() && !isComplete(ids, total)) {
				throw invalid("Game search ended before the reported total");
			}
		}
		if (!isComplete(ids, total) || ids.size() != total) {
			throw invalid("Game search result is incomplete");
		}
		return ids;
	}

	/** İlk sayfada toplamı sabitler ve üst sınırı denetler; sonraki sayfalarda toplamın değişmediğini doğrular. */
	private long checkTotal(long knownTotal, long reportedTotal) {
		if (knownTotal < 0) {
			if (reportedTotal > maxGames) {
				throw new GameSearchTooBroadException(maxGames);
			}
			return reportedTotal;
		}
		if (reportedTotal != knownTotal) {
			throw invalid("Game catalog changed during the search");
		}
		return knownTotal;
	}

	private static void collect(GameSearchResponse response, Set<UUID> ids) {
		for (GameSearchResponse.Item item : response.items()) {
			if (item == null || item.game() == null || item.game().gameId() == null) {
				throw invalid("Game search item without gameId");
			}
			ids.add(item.game().gameId());
		}
	}

	private static boolean isComplete(Set<UUID> ids, long total) {
		return total >= 0 && ids.size() >= total;
	}

	private GameSearchResponse fetchSearchPage(String text, int page) {
		try {
			return restClient.get()
					.uri(uri -> uri.path(searchPath)
							.queryParam("q", "{q}")
							.queryParam("page", page)
							.queryParam("size", SEARCH_PAGE_SIZE)
							.build(text))
					.retrieve()
					.body(GameSearchResponse.class);
		} catch (RestClientException exception) {
			throw new GameCatalogUnavailableException(exception);
		}
	}

	private static void validate(GameSearchResponse response) {
		if (response == null || response.items() == null || response.totalElements() < 0
				|| response.items().size() > SEARCH_PAGE_SIZE) {
			throw invalid("Invalid game search response");
		}
	}

	private static GameCatalogUnavailableException invalid(String message) {
		return new GameCatalogUnavailableException(new IllegalStateException(message));
	}
}

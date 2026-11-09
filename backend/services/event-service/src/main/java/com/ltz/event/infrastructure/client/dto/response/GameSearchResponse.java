package com.ltz.event.infrastructure.client.dto.response;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * {@code GET /api/v1/game/games/search} yanıtından (GameDiscoveryResponse) yalnız gerekli alanlar: oyun kimlikleri,
 * sayfa ve toplam. Bilinmeyen alanlar yok sayılır. Yanıt güvenilmez girdidir; istemci doğrular.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GameSearchResponse(List<Item> items, int page, int size, long totalElements) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Item(Card game) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Card(UUID gameId) {
	}
}

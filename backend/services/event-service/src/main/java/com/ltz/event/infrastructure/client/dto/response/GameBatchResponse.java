package com.ltz.event.infrastructure.client.dto.response;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Yalnız ihtiyaç duyulan alanlar okunur; Game Service kart alanları genişleyebilir (bilinmeyen alanlar yok sayılır). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GameBatchResponse(List<Item> items, List<UUID> unavailable) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Item(UUID gameId) {
	}
}

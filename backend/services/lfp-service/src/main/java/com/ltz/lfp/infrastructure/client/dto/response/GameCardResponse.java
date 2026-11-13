package com.ltz.lfp.infrastructure.client.dto.response;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * game-service {@code GET /api/v1/game/{gameId}/card} yanıtından okunan alanlar (docs/api.md → GAME → Ortak oyun
 * kartı). Diğer alanlar (kapak, köken, dış referanslar) kullanılmaz; sözleşme büyüdüğünde kırılmasın diye bilinmeyen
 * alanlar yok sayılır.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GameCardResponse(UUID gameId, String title, String gameType) {
}

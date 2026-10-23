package com.ltz.game.api.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * {@code items}: istek sırasıyla herkese açık kartlar. {@code unavailable}: bulunamayan, arşivlenmiş veya herkese kapalı
 * kimlikler; bu nedenler birbirinden ayırt edilmez.
 */
public record GameBatchResponse(List<GameCardResponse> items, List<UUID> unavailable) { }

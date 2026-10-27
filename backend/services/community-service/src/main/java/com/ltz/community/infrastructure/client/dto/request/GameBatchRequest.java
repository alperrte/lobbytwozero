package com.ltz.community.infrastructure.client.dto.request;

import java.util.List;
import java.util.UUID;

/** game-service {@code POST /api/v1/game/games/batch} istek gövdesi (docs/api.md GAME-15). */
public record GameBatchRequest(List<UUID> gameIds) {
}

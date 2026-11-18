package com.ltz.event.infrastructure.client.dto.request;

import java.util.List;
import java.util.UUID;

/** {@code POST /api/v1/game/games/batch} isteği (1–50 kimlik). */
public record GameBatchRequest(List<UUID> gameIds) {
}

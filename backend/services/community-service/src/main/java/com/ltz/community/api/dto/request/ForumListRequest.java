package com.ltz.community.api.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** Forum listesi: isteğe bağlı oyun filtresi ve keyset sayfalama ({@code limit} varsayılanı 20, üst sınırı 50). */
public record ForumListRequest(
		UUID gameId,
		@Min(1) @Max(50) Integer limit,
		@Size(max = 200) String cursor) {
}

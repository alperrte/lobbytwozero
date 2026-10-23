package com.ltz.game.api.dto.response;

import java.time.Instant;
import java.util.List;

public record CatalogSearchResponse(List<GameCardResponse> items, int page, int size, long totalElements,
        Instant lastSuccessfulAt, Long providerTotal) { }

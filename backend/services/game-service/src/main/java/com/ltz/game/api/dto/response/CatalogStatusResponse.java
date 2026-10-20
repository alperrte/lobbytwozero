package com.ltz.game.api.dto.response;

import com.ltz.game.domain.enums.*;
import java.time.Instant;

public record CatalogStatusResponse(String provider, CatalogRunMode runMode, CatalogSyncStatus syncStatus,
        long cursor, long processedCount, long indexedEntries, long mainGameEntries, long unclassifiedEntries, Long providerTotal,
        Instant runStartedAt, Instant lastSuccessfulAt, String lastErrorCode) { }

package com.ltz.library.api.dto.response;

import com.ltz.library.domain.enums.GameSummaryStatus;
import java.util.UUID;

public record LibraryGameResponse(UUID gameId, String title, String coverUrl, String origin,
        String metadataCompleteness, String gameType, GameSummaryStatus status) { }

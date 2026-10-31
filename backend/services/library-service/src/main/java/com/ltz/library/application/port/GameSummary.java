package com.ltz.library.application.port;

import java.util.UUID;

public record GameSummary(UUID gameId, String title, String coverUrl, String origin,
        String metadataCompleteness, String gameType) { }

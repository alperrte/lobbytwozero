package com.ltz.library.infrastructure.client.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GameBatchResponse(List<Card> items, List<UUID> unavailable) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Card(UUID gameId, String title, String coverUrl, String origin,
            String metadataCompleteness, String gameType) { }
}

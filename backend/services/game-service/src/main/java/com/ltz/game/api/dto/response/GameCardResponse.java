package com.ltz.game.api.dto.response;

import com.ltz.game.domain.enums.GameOrigin;
import com.ltz.game.domain.enums.GameType;
import com.ltz.game.domain.enums.MetadataCompleteness;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GameCardResponse(UUID gameId, String title, GameOrigin origin,
        MetadataCompleteness metadataCompleteness, GameType gameType, List<ExternalReference> externalReferences, String coverUrl) {
    public record ExternalReference(String provider, String externalId, Instant lastSyncedAt) { }
}

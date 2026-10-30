package com.ltz.library.api.dto.response;

import java.time.Instant;
import java.util.UUID;

public record LibraryEntryResponse(UUID id, UUID gameId, boolean inCollection,
        boolean favorite, boolean completed, Instant addedAt, Instant updatedAt, long version) { }

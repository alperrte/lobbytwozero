package com.ltz.library.application.mapper;

import com.ltz.library.api.dto.response.LibraryEntryResponse;
import com.ltz.library.domain.entity.LibraryEntry;

public final class LibraryEntryMapper {
    private LibraryEntryMapper() { }

    public static LibraryEntryResponse response(LibraryEntry entry) {
        return new LibraryEntryResponse(entry.getId(), entry.getGameId(), entry.isInCollection(),
                entry.isFavorite(), entry.isCompleted(), entry.getAddedAt(), entry.getUpdatedAt(), entry.getVersion());
    }
}

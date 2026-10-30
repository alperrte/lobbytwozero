package com.ltz.library.application.service;

import com.ltz.library.api.dto.response.LibraryEntryResponse;
import com.ltz.library.domain.enums.LibraryAction;
import com.ltz.library.infrastructure.persistence.repository.LibraryEntryRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Yalnız yerel DB işlemi transaction'a dahildir. */
@Service
public class LibraryEntryWriter {
    private final LibraryEntryRepository entries;

    public LibraryEntryWriter(LibraryEntryRepository entries) { this.entries = entries; }

    @Transactional(timeout = 4)
    public LibraryEntryResponse write(UUID userId, UUID gameId, LibraryAction action) {
        var entry = entries.write(userId, gameId, action);
        return new LibraryEntryResponse(entry.getId(), entry.getGameId(), entry.isInCollection(),
                entry.isFavorite(), entry.isCompleted(), entry.getAddedAt(), entry.getUpdatedAt(), entry.getVersion());
    }

    @Transactional(timeout = 4)
    public void remove(UUID userId, UUID gameId) { entries.remove(userId, gameId); }
}

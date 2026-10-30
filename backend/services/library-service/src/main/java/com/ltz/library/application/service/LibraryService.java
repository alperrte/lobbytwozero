package com.ltz.library.application.service;

import com.ltz.library.api.dto.response.LibraryEntryResponse;
import com.ltz.library.application.port.GameCatalogPort;
import com.ltz.library.domain.enums.LibraryAction;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class LibraryService {
    private final GameCatalogPort catalog;
    private final LibraryEntryWriter writer;

    public LibraryService(GameCatalogPort catalog, LibraryEntryWriter writer) {
        this.catalog = catalog;
        this.writer = writer;
    }

    public LibraryEntryResponse add(UUID userId, UUID gameId) {
        return write(userId, gameId, LibraryAction.COLLECT);
    }

    public LibraryEntryResponse favorite(UUID userId, UUID gameId) {
        return write(userId, gameId, LibraryAction.FAVORITE);
    }

    public LibraryEntryResponse complete(UUID userId, UUID gameId) {
        return write(userId, gameId, LibraryAction.COMPLETE);
    }

    private LibraryEntryResponse write(UUID userId, UUID gameId, LibraryAction action) {
        catalog.requireAvailable(gameId);
        return writer.write(userId, gameId, action);
    }

    /** Kaldırma dış katalogdan bağımsızdır; arşivlenen kayıt da kaldırılabilir. */
    public void remove(UUID userId, UUID gameId) { writer.remove(userId, gameId); }
}

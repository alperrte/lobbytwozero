package com.ltz.library.application.service;

import com.ltz.library.api.dto.response.LibraryEntryResponse;
import com.ltz.library.api.dto.response.LibraryEntryPageResponse;
import com.ltz.library.application.port.GameCatalogPort;
import com.ltz.library.domain.enums.LibraryAction;
import com.ltz.library.domain.enums.LibraryFlag;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class LibraryService {
    private final GameCatalogPort catalog;
    private final LibraryEntryWriter writer;
    private final LibraryEntryReader reader;

    public LibraryService(GameCatalogPort catalog, LibraryEntryWriter writer, LibraryEntryReader reader) {
        this.catalog = catalog;
        this.writer = writer;
        this.reader = reader;
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

    public void unfavorite(UUID userId, UUID gameId) { writer.clearFlag(userId, gameId, LibraryFlag.FAVORITE); }

    public LibraryEntryPageResponse favorites(UUID userId, int page, int size) {
        return reader.read(userId, LibraryFlag.FAVORITE, page, size);
    }

    public void uncomplete(UUID userId, UUID gameId) { writer.clearFlag(userId, gameId, LibraryFlag.COMPLETED); }

    public LibraryEntryPageResponse completed(UUID userId, int page, int size) {
        return reader.read(userId, LibraryFlag.COMPLETED, page, size);
    }
}

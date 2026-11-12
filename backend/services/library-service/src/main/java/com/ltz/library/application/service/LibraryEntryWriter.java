package com.ltz.library.application.service;

import com.ltz.library.api.dto.response.LibraryEntryResponse;
import com.ltz.library.domain.enums.LibraryAction;
import com.ltz.library.domain.enums.LibraryFlag;
import com.ltz.library.application.mapper.LibraryEntryMapper;
import com.ltz.library.infrastructure.persistence.repository.LibraryEntryRepository;
import com.ltz.library.infrastructure.persistence.repository.LibrarySummaryOutboxRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Yalnız yerel DB işlemi transaction'a dahildir; profil özeti işareti (LIB-10) veriyle aynı transaction'da yazılır. */
@Service
public class LibraryEntryWriter {
    private final LibraryEntryRepository entries;
    private final LibrarySummaryOutboxRepository summaries;

    public LibraryEntryWriter(LibraryEntryRepository entries, LibrarySummaryOutboxRepository summaries) {
        this.entries = entries;
        this.summaries = summaries;
    }

    @Transactional(timeout = 4)
    public LibraryEntryResponse write(UUID userId, UUID gameId, LibraryAction action) {
        var entry = entries.write(userId, gameId, action);
        summaries.mark(userId);
        return LibraryEntryMapper.response(entry);
    }

    @Transactional(timeout = 4)
    public void remove(UUID userId, UUID gameId) {
        entries.remove(userId, gameId);
        summaries.mark(userId);
    }

    @Transactional(timeout = 4)
    public void clearFlag(UUID userId, UUID gameId, LibraryFlag flag) {
        entries.clearFlag(userId, gameId, flag);
        summaries.mark(userId);
    }
}

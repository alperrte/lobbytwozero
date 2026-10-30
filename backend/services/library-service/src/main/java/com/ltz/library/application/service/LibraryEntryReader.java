package com.ltz.library.application.service;

import com.ltz.library.api.dto.response.LibraryEntryPageResponse;
import com.ltz.library.application.mapper.LibraryEntryMapper;
import com.ltz.library.domain.enums.LibraryFlag;
import com.ltz.library.domain.enums.SortDirection;
import com.ltz.library.api.dto.response.LibraryEntryResponse;
import com.ltz.library.api.dto.response.LibraryStatisticsResponse;
import java.util.List;
import com.ltz.library.infrastructure.persistence.repository.LibraryEntryRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LibraryEntryReader {
    private final LibraryEntryRepository entries;

    public LibraryEntryReader(LibraryEntryRepository entries) { this.entries = entries; }

    /** Liste ve toplam aynı DB snapshot'ını kullanır; katalog HTTP çağrısı yapılmaz. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 4)
    public LibraryEntryPageResponse read(UUID userId, LibraryFlag flag, int page, int size) {
        var items = entries.findFlagged(userId, flag, page, size).stream().map(LibraryEntryMapper::response).toList();
        return new LibraryEntryPageResponse(items, page, size, entries.countFlagged(userId, flag));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 4)
    public LibraryEntryPageResponse collection(UUID userId, Boolean favorite, Boolean completed, SortDirection direction, int page, int size) {
        var items = entries.findCollection(userId, favorite, completed, direction, page, size).stream().map(LibraryEntryMapper::response).toList();
        return new LibraryEntryPageResponse(items, page, size, entries.countCollection(userId, favorite, completed));
    }

    @Transactional(readOnly = true, timeout = 4)
    public List<LibraryEntryResponse> snapshot(UUID userId, Boolean favorite, Boolean completed) {
        return entries.collectionSnapshot(userId, favorite, completed).stream().map(LibraryEntryMapper::response).toList();
    }

    @Transactional(readOnly = true, timeout = 4)
    public LibraryStatisticsResponse statistics(UUID userId) {
        var counts = entries.statistics(userId);
        return new LibraryStatisticsResponse(counts.total(), counts.favorite(), counts.completed());
    }
}

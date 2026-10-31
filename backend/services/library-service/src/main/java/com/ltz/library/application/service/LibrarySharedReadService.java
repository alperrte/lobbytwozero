package com.ltz.library.application.service;

import com.ltz.library.api.dto.response.LibraryCollectionPageResponse;
import com.ltz.library.api.dto.response.LibraryStatisticsResponse;
import com.ltz.library.api.dto.response.LibrarySignalPageResponse;
import com.ltz.library.domain.enums.CollectionSort;
import com.ltz.library.domain.enums.SortDirection;
import com.ltz.library.domain.enums.LibraryAccessPurpose;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class LibrarySharedReadService {
    private final LibraryAccessService access;
    private final LibraryCollectionService collection;
    private final LibraryEntryReader reader;

    public LibrarySharedReadService(LibraryAccessService access, LibraryCollectionService collection, LibraryEntryReader reader) {
        this.access = access;
        this.collection = collection;
        this.reader = reader;
    }

    public LibraryCollectionPageResponse profile(UUID owner, UUID viewer, int page, int size) {
        access.require(owner, viewer, LibraryAccessPurpose.PROFILE);
        return collection.list(owner, null, null, CollectionSort.ADDED_AT, SortDirection.DESC, page, size);
    }

    public LibraryStatisticsResponse statistics(UUID owner, UUID viewer) {
        access.require(owner, viewer, LibraryAccessPurpose.PROFILE);
        return collection.statistics(owner);
    }

    public LibrarySignalPageResponse signals(UUID owner, int page, int size) {
        access.require(owner, owner, LibraryAccessPurpose.RECOMMENDATION);
        return reader.signals(owner, page, size);
    }
}

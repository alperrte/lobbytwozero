package com.ltz.library.api.dto.response;

import java.util.List;

public record LibraryCollectionPageResponse(List<LibraryCollectionItemResponse> items, int page, int size,
        long totalElements, boolean metadataPartial) { }

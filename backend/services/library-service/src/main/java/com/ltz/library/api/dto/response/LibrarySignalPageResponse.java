package com.ltz.library.api.dto.response;

import java.util.List;

public record LibrarySignalPageResponse(List<LibrarySignalResponse> items, int page, int size, long totalElements) { }

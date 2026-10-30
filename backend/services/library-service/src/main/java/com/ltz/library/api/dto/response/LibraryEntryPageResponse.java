package com.ltz.library.api.dto.response;

import java.util.List;

/** Yalnız favori/bitirildi listeleri; oyun metadata'sı sonraki fazın sözleşmesidir. */
public record LibraryEntryPageResponse(List<LibraryEntryResponse> items, int page, int size, long totalElements) { }

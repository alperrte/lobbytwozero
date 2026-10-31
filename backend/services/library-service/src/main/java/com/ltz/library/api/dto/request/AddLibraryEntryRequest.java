package com.ltz.library.api.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AddLibraryEntryRequest(@NotNull UUID gameId) { }

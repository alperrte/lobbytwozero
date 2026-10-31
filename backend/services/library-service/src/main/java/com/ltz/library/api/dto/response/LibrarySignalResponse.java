package com.ltz.library.api.dto.response;

import java.util.UUID;

public record LibrarySignalResponse(UUID gameId, boolean favorite, boolean completed) { }

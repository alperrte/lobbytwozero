package com.ltz.game.api.dto.response;

import java.util.UUID;

public record ManagedGameResponse(UUID gameId, String title) { }

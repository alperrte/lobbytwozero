package com.ltz.game.api.dto.response;

import java.time.Instant;

public record DetailFreshnessResponse(String status, Instant fetchedAt, Instant expiresAt,
        Instant nextAttemptAt, String lastErrorCode) { }

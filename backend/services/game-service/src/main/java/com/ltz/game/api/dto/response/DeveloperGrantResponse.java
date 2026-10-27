package com.ltz.game.api.dto.response;

import java.time.Instant;
import java.util.UUID;

public record DeveloperGrantResponse(UUID grantId, UUID gameId, UUID developerUserId, Instant grantedAt) { }

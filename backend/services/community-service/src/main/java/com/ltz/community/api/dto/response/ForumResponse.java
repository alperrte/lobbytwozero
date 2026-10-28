package com.ltz.community.api.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.ltz.community.domain.enums.ForumStatus;

public record ForumResponse(
		UUID id,
		UUID gameId,
		String slug,
		String title,
		String description,
		ForumStatus status,
		Instant createdAt,
		Instant updatedAt) {
}

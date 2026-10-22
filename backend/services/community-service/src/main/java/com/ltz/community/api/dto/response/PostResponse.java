package com.ltz.community.api.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.ltz.community.domain.enums.PostCategory;
import com.ltz.community.domain.enums.PostStatus;

public record PostResponse(
		UUID id,
		UUID authorId,
		UUID gameId,
		PostCategory category,
		String body,
		PostStatus status,
		Instant createdAt,
		Instant updatedAt) {
}

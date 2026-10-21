package com.ltz.community.api.dto.response;

import java.time.Instant;
import java.util.UUID;

public record PostImageResponse(
		UUID id,
		UUID postId,
		String mimeType,
		int sizeBytes,
		int width,
		int height,
		int sortOrder,
		String contentUrl,
		Instant createdAt) {
}

package com.ltz.content.api.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ContentImageResponse(
		UUID id,
		UUID contentId,
		String mimeType,
		int sizeBytes,
		int width,
		int height,
		int sortOrder,
		String contentUrl,
		Instant createdAt) {
}

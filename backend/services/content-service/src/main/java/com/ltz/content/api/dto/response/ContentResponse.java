package com.ltz.content.api.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ltz.content.domain.enums.ContentStatus;
import com.ltz.content.domain.enums.ContentType;

/** Taslak/içerik görünümü; metin içeriğin ilk dilindeki çevirisidir ({@code locale}). */
public record ContentResponse(
		UUID id,
		UUID authorId,
		ContentType type,
		ContentStatus status,
		String locale,
		String title,
		String summary,
		String body,
		List<ContentImageResponse> images,
		Instant createdAt,
		Instant updatedAt) {
}

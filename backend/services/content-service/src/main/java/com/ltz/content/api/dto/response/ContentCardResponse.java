package com.ltz.content.api.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ltz.content.domain.enums.ContentType;

/** Akış kartı: gövde taşımaz; {@code coverImageUrl} ilk görselin adresidir (görsel yoksa null). */
public record ContentCardResponse(
		UUID id,
		UUID authorId,
		ContentType type,
		String locale,
		String title,
		String summary,
		UUID gameId,
		List<String> categories,
		String coverImageUrl,
		boolean featured,
		Instant publishedAt) {
}

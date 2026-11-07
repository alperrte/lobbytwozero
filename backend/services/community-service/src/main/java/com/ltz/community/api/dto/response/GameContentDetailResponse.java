package com.ltz.community.api.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ltz.community.domain.enums.GameContentType;

/** Oyuna bağlı içeriğin detayı (GAME-11). {@code body} düz metindir; istemci metin olarak göstermelidir. */
public record GameContentDetailResponse(
		UUID id,
		UUID gameId,
		GameContentType type,
		String title,
		String body,
		UUID authorId,
		Instant publishedAt,
		Instant updatedAt,
		int likeCount,
		int commentCount,
		boolean likedByMe,
		List<PostImageResponse> images) {
}

package com.ltz.community.api.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ltz.community.domain.enums.PostCategory;
import com.ltz.community.domain.enums.PostStatus;

/**
 * Okuma görünümü (akış ve detay): yazar kimliği, oyun bağlantısı, kategori, etkileşim sayaçları ve görsel
 * bilgileri ile. Sayaçlar Faz 3'te (beğeni/yorum) dolar; yorum listesi ayrı uçtan (COM-07) sunulacaktır.
 */
public record PostViewResponse(
		UUID id,
		UUID authorId,
		UUID gameId,
		PostCategory category,
		String body,
		PostStatus status,
		Instant createdAt,
		Instant updatedAt,
		int likeCount,
		int commentCount,
		List<PostImageResponse> images) {
}

package com.ltz.community.api.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ltz.community.domain.enums.PostCategory;
import com.ltz.community.domain.enums.PostStatus;

/**
 * Okuma görünümü (akış ve detay): yazar kimliği, oyun bağlantısı, kategori, etkileşim sayaçları ve görsel
 * bilgileri ile. {@code likedByMe} çağıranın beğenisini gösterir (misafir için her zaman false); yorumlar ayrı uçtan
 * (COM-07) sunulur. Repost ise ({@code repostOf} dolu) kaynak gönderi ve sahibi açık atıf olarak gömülüdür; bu durumda
 * {@code body} reposter'ın isteğe bağlı kısa yorumudur (null olabilir) ve {@code authorId} reposter'dır (COM-20).
 */
public record PostViewResponse(
		UUID id,
		UUID authorId,
		UUID gameId,
		UUID forumId,
		PostCategory category,
		String body,
		PostStatus status,
		Instant createdAt,
		Instant updatedAt,
		int likeCount,
		int commentCount,
		boolean likedByMe,
		List<PostImageResponse> images,
		RepostSourceResponse repostOf) {
}

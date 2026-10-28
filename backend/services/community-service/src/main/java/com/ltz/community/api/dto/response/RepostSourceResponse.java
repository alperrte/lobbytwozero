package com.ltz.community.api.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ltz.community.domain.enums.PostCategory;

/**
 * Repost'un kaynağı (orijinal gönderi). {@code available = true} iken orijinalin sahibi, içeriği ve görselleri açık
 * atıf olarak gelir. Orijinal silinmiş, gizlenmiş ya da (gizli forum nedeniyle) erişilemezse yalnız {@code postId}
 * ve {@code available = false} döner; içerik ve sahip bilgisi sızdırılmaz.
 */
public record RepostSourceResponse(
		UUID postId,
		boolean available,
		UUID authorId,
		UUID gameId,
		PostCategory category,
		String body,
		Instant createdAt,
		List<PostImageResponse> images) {

	public static RepostSourceResponse unavailable(UUID postId) {
		return new RepostSourceResponse(postId, false, null, null, null, null, null, List.of());
	}
}

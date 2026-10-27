package com.ltz.community.api.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.ltz.community.domain.enums.CommentStatus;

/**
 * Yorum görünümü. Silinmiş (DELETED) yorum yalnız yanıtları olduğu için ilişkiyi korumak amacıyla listelenir ve
 * içerik ile yazar bilgisi ({@code authorId}, {@code body}) null döner. {@code replyCount} doğrudan, herkese açık
 * yanıt sayısıdır.
 */
public record CommentResponse(
		UUID id,
		UUID postId,
		UUID authorId,
		UUID parentCommentId,
		String body,
		CommentStatus status,
		Instant createdAt,
		Instant updatedAt,
		int replyCount) {
}

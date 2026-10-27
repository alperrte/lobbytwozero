package com.ltz.community.api.dto.request;

import java.util.UUID;

import com.ltz.community.domain.entity.Comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code parentCommentId} verilirse yorum, aynı gönderideki o yoruma yanıt olur (COM-09). */
public record CreateCommentRequest(
		@NotBlank
		@Size(max = Comment.BODY_MAX_LENGTH)
		@Pattern(regexp = Comment.BODY_ALLOWED_CHARACTERS, message = "must not contain control characters")
		String body,
		UUID parentCommentId) {
}

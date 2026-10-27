package com.ltz.community.api.dto.request;

import com.ltz.community.domain.entity.Post;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** İsteğe bağlı kısa yorum; boş ya da gönderilmezse düz repost oluşturulur. */
public record CreateRepostRequest(
		@Size(max = CreateRepostRequest.COMMENT_MAX_LENGTH)
		@Pattern(regexp = Post.BODY_ALLOWED_CHARACTERS, message = "must not contain control characters")
		String comment) {

	public static final int COMMENT_MAX_LENGTH = 500;
}

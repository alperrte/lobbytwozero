package com.ltz.community.api.dto.request;

import java.util.UUID;

import com.ltz.community.domain.entity.Post;
import com.ltz.community.domain.enums.PostCategory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code gameId} isteğe bağlıdır; verilirse LTZ oyun kataloğunda doğrulanır (COM-14). {@code category}
 * isteğe bağlıdır; verilmezse {@code GENERAL} atanır (COM-12).
 */
public record CreatePostRequest(
		@NotBlank
		@Size(max = Post.BODY_MAX_LENGTH)
		@Pattern(regexp = Post.BODY_ALLOWED_CHARACTERS, message = "must not contain control characters")
		String body,
		UUID gameId,
		PostCategory category) {
}

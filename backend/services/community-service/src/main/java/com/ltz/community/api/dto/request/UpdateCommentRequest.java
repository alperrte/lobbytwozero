package com.ltz.community.api.dto.request;

import com.ltz.community.domain.entity.Comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateCommentRequest(
		@NotBlank
		@Size(max = Comment.BODY_MAX_LENGTH)
		@Pattern(regexp = Comment.BODY_ALLOWED_CHARACTERS, message = "must not contain control characters")
		String body) {
}

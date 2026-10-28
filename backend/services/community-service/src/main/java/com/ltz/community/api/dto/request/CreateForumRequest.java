package com.ltz.community.api.dto.request;

import java.util.UUID;

import com.ltz.community.domain.entity.Forum;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code gameId} isteğe bağlıdır; verilirse LTZ oyun kataloğunda doğrulanır ve forum o oyuna bağlanır. {@code slug}
 * benzersizdir ve sonradan değişmez.
 */
public record CreateForumRequest(
		@NotBlank
		@Size(min = 3, max = 80)
		@Pattern(regexp = Forum.SLUG_PATTERN, message = "must contain lowercase letters, digits and single hyphens only")
		String slug,
		@NotBlank
		@Size(max = Forum.TITLE_MAX_LENGTH)
		@Pattern(regexp = Forum.TEXT_ALLOWED_CHARACTERS, message = "must not contain control characters")
		String title,
		@Size(max = Forum.DESCRIPTION_MAX_LENGTH)
		@Pattern(regexp = Forum.TEXT_ALLOWED_CHARACTERS, message = "must not contain control characters")
		String description,
		UUID gameId) {
}

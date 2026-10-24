package com.ltz.community.api.dto.request;

import com.ltz.community.domain.entity.Forum;
import com.ltz.community.domain.enums.ForumStatus;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Moderatör güncellemesi: gönderilmeyen (null) alanlar korunur. {@code slug} ve {@code gameId} değiştirilemez.
 * {@code status = HIDDEN} forumu ve konularını herkesten gizler; {@code VISIBLE} yeniden açar.
 */
public record UpdateForumRequest(
		@Size(max = Forum.TITLE_MAX_LENGTH)
		@Pattern(regexp = "\\s*+\\S.*+", flags = Pattern.Flag.DOTALL, message = "must not be blank")
		@Pattern(regexp = Forum.TEXT_ALLOWED_CHARACTERS, message = "must not contain control characters")
		String title,
		@Size(max = Forum.DESCRIPTION_MAX_LENGTH)
		@Pattern(regexp = Forum.TEXT_ALLOWED_CHARACTERS, message = "must not contain control characters")
		String description,
		ForumStatus status) {
}

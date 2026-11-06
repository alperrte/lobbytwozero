package com.ltz.content.api.dto.request;

import java.time.LocalDate;

import com.ltz.content.domain.entity.ContentSource;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

/** Bir kaynak: ad, yalnız {@code https://} bağlantısı ve (isteğe bağlı) geçmiş ya da bugünkü kaynak tarihi. */
public record SourceRequest(
		@NotBlank
		@Size(max = ContentSource.TITLE_MAX_LENGTH)
		@Pattern(regexp = "[^\\x00-\\x1F\\x7F]*", message = "must not contain control characters")
		String title,
		@NotBlank
		@Size(max = ContentSource.URL_MAX_LENGTH)
		@Pattern(regexp = "https://\\S+", message = "must be an https:// link without spaces")
		String url,
		@PastOrPresent
		LocalDate date) {
}

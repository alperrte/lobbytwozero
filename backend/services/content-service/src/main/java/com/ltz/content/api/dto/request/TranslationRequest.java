package com.ltz.content.api.dto.request;

import com.ltz.content.domain.entity.ContentTranslation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Bir dildeki çevirinin tamamı (tam değiştirme): başlık zorunlu, özet ve gövde boş bırakılırsa temizlenir. */
public record TranslationRequest(
		@NotBlank
		@Size(max = ContentTranslation.TITLE_MAX_LENGTH)
		@Pattern(regexp = ContentTranslation.TITLE_ALLOWED_CHARACTERS, message = "must not contain control characters")
		String title,
		@Size(max = ContentTranslation.SUMMARY_MAX_LENGTH)
		@Pattern(regexp = ContentTranslation.TEXT_ALLOWED_CHARACTERS, message = "must not contain control characters")
		String summary,
		@Size(max = ContentTranslation.BODY_MAX_LENGTH)
		@Pattern(regexp = ContentTranslation.TEXT_ALLOWED_CHARACTERS, message = "must not contain control characters")
		String body) {
}

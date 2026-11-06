package com.ltz.content.api.dto.request;

import com.ltz.content.domain.entity.ContentTranslation;
import com.ltz.content.domain.enums.ContentType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Yeni taslak. Yazar kimliği ve yayın durumu istekte yer almaz (bilinmeyen alanlar reddedilir): yazar doğrulanmış
 * oturumdan, durum her zaman {@code DRAFT} olarak sunucuda belirlenir. {@code locale} verilmezse {@code tr} kullanılır.
 * Özet ve gövde taslakta boş bırakılabilir.
 */
public record CreateContentRequest(
		@NotNull
		ContentType type,
		@Pattern(regexp = "tr|en", message = "must be one of: tr, en")
		String locale,
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

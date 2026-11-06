package com.ltz.content.api.dto.request;

import com.ltz.content.domain.entity.ContentTranslation;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Taslak düzenleme (kısmi güncelleme): yalnız verilen alanlar değişir, verilmeyen (null) alan olduğu gibi kalır.
 * {@code summary} ya da {@code body} için boş metin alanı temizler; başlık boş olamaz. En az bir alan verilmelidir. Yazar,
 * tür, dil ve durum bu istekle değiştirilemez (bilinmeyen alanlar reddedilir).
 */
public record UpdateContentRequest(
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

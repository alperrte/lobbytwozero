package com.ltz.content.api.dto.request;

import java.time.Instant;
import java.util.UUID;

import com.ltz.content.domain.enums.ContentType;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Herkese açık içerik akışı sorgu parametreleri. {@code limit} varsayılanı 20, üst sınırı 50'dir. {@code q} (CONT-12) başlık,
 * özet ve gövdede arar (en az 2, en çok 100 karakter, kontrol karakteri içermez). Filtreler (CONT-13): {@code category}
 * (kategori kodu), {@code gameId} (ilişkili oyun), {@code type}, {@code featured} (CONT-11), {@code from} (dahil) ve
 * {@code to} (hariç) yayınlanma zamanı (ISO-8601). {@code locale} gösterilecek dildir ({@code tr} varsayılan; çeviri yoksa
 * içeriğin ilk dili). Sıralama yayınlanma zamanına göre yeniden eskiye sabittir.
 */
public record ContentFeedRequest(
		@Size(max = 100) @Pattern(regexp = "[^\\x00-\\x1F\\x7F]*", message = "must not contain control characters") String q,
		@Size(max = 32) @Pattern(regexp = "[A-Z][A-Z0-9_]*", message = "must be an upper-case category code") String category,
		UUID gameId,
		ContentType type,
		Boolean featured,
		Instant from,
		Instant to,
		@Pattern(regexp = "tr|en", message = "must be one of: tr, en") String locale,
		@Min(1) @Max(50) Integer limit,
		@Size(max = 200) String cursor) {
}

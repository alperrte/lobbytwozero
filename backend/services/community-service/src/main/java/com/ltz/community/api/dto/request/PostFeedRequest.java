package com.ltz.community.api.dto.request;

import java.time.Instant;
import java.util.UUID;

import com.ltz.community.domain.enums.PostCategory;
import com.ltz.community.domain.enums.PostSort;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Gönderi akışı sorgu parametreleri. {@code limit} varsayılanı 20, üst sınırı 50'dir; {@code q} gönderi
 * içeriğinde arar (en az 2, en çok 100 karakter, kontrol karakteri içermez). Filtreler (COM-12): {@code gameId}
 * (ilişkili oyun), {@code category}, {@code from} (dahil) ve {@code to} (hariç) oluşturulma zamanı (ISO-8601).
 * Sıralama (COM-13): {@code sort} = NEWEST (varsayılan), OLDEST veya POPULAR.
 */
public record PostFeedRequest(
		@Size(max = 100) @Pattern(regexp = "[^\\x00-\\x1F\\x7F]*", message = "must not contain control characters") String q,
		UUID gameId,
		PostCategory category,
		Instant from,
		Instant to,
		PostSort sort,
		@Min(1) @Max(50) Integer limit,
		@Size(max = 200) String cursor) {
}

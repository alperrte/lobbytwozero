package com.ltz.community.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** Yorum/yanıt/forum listeleri için keyset sayfalama: {@code limit} varsayılanı 20, üst sınırı 50. */
public record CommentPageRequest(
		@Min(1) @Max(50) Integer limit,
		@Size(max = 200) String cursor) {
}

package com.ltz.content.api.dto.request;

import java.util.List;

import com.ltz.content.domain.entity.ContentCategoryAssignment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** İçeriğin kategorilerini tamamen değiştirir; boş liste tüm kategorileri kaldırır. En çok 5 kategori kodu. */
public record ReplaceCategoriesRequest(
		@NotNull
		@Size(max = ContentCategoryAssignment.MAX_PER_CONTENT)
		List<@NotBlank @Size(max = 32) String> categories) {
}

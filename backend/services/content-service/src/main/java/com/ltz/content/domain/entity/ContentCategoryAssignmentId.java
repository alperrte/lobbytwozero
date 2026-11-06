package com.ltz.content.domain.entity;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** İçerik-kategori ilişkisinin bileşik anahtarı: bir içerik bir kategoriye en çok bir kez bağlanır. */
@Embeddable
public record ContentCategoryAssignmentId(
		@Column(name = "content_id", nullable = false, updatable = false) UUID contentId,
		@Column(name = "category_id", nullable = false, updatable = false) UUID categoryId) implements Serializable {
}

package com.ltz.content.domain.enums;

/**
 * İçerik yaşam döngüsü (docs/architecture.md): {@code DRAFT → PUBLISHED → REMOVED}, {@code PUBLISHED → ARCHIVED}. Bu fazda
 * yalnız {@code DRAFT} oluşturulur; diğer durumlar yayınlama ve moderasyon fazlarında kullanılır.
 */
public enum ContentStatus {
	DRAFT,
	PUBLISHED,
	REMOVED,
	ARCHIVED
}

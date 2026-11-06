package com.ltz.content.domain.entity;

import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Kaynağa dayalı haberlerde kaynak (CONT-16): ad, https bağlantısı ve kaynak tarihi. Bağlantı yalnız saklanır ve gösterilir;
 * sunucu bu adrese istek atmaz. İçerik başına en çok {@link #MAX_PER_CONTENT} kaynak tutulur.
 */
@Entity
@Table(name = "content_sources")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentSource {

	public static final int MAX_PER_CONTENT = 3;
	public static final int TITLE_MAX_LENGTH = 150;
	public static final int URL_MAX_LENGTH = 500;

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(name = "content_id", nullable = false, updatable = false)
	private UUID contentId;

	@Column(nullable = false, updatable = false, length = TITLE_MAX_LENGTH)
	private String title;

	@Column(nullable = false, updatable = false, length = URL_MAX_LENGTH)
	private String url;

	@Column(name = "source_date", updatable = false)
	private LocalDate sourceDate;

	@Column(name = "sort_order", nullable = false, updatable = false)
	private int sortOrder;

	public static ContentSource of(UUID contentId, String title, String url, LocalDate sourceDate, int sortOrder) {
		ContentSource source = new ContentSource();
		source.contentId = contentId;
		source.title = title;
		source.url = url;
		source.sourceDate = sourceDate;
		source.sortOrder = sortOrder;
		return source;
	}
}

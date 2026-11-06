package com.ltz.content.domain.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Taslak/içerik görseli; baytlar sunucuda yeniden kodlanmış JPEG/PNG'dir (ayrı media-service yoktur). */
@Entity
@Table(name = "content_images")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentImage {

	public static final int MAX_IMAGES_PER_CONTENT = 6;
	public static final int MAX_SIZE_BYTES = 2 * 1024 * 1024;
	public static final int MAX_SIDE_PIXELS = 6000;
	public static final int MAX_PIXELS = 12_000_000;

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(name = "content_id", nullable = false, updatable = false)
	private UUID contentId;

	@Column(nullable = false, updatable = false)
	private byte[] content;

	@Column(name = "mime_type", nullable = false, updatable = false, length = 32)
	private String mimeType;

	@Column(name = "size_bytes", nullable = false, updatable = false)
	private int sizeBytes;

	@Column(nullable = false, updatable = false)
	private int width;

	@Column(nullable = false, updatable = false)
	private int height;

	@Column(name = "sort_order", nullable = false, updatable = false)
	private int sortOrder;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public static ContentImage attach(UUID contentId, byte[] content, String mimeType, int width, int height,
			int sortOrder, Instant now) {
		ContentImage image = new ContentImage();
		image.contentId = contentId;
		image.content = content;
		image.mimeType = mimeType;
		image.sizeBytes = content.length;
		image.width = width;
		image.height = height;
		image.sortOrder = sortOrder;
		image.createdAt = now;
		return image;
	}
}

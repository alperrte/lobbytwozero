package com.ltz.community.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.UUID;

/** Görsel baytlarını yüklemeden listeleme için kapalı projeksiyon. */
public interface PostImageMeta {

	UUID getId();

	UUID getPostId();

	String getMimeType();

	int getSizeBytes();

	int getWidth();

	int getHeight();

	int getSortOrder();

	Instant getCreatedAt();
}

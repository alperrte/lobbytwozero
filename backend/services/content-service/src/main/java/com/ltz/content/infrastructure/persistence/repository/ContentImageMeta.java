package com.ltz.content.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.UUID;

/** Görsel baytlarını yüklemeden listeleme için kapalı projeksiyon. */
public interface ContentImageMeta {

	UUID getId();

	UUID getContentId();

	String getMimeType();

	int getSizeBytes();

	int getWidth();

	int getHeight();

	int getSortOrder();

	Instant getCreatedAt();
}

package com.ltz.content.api.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ltz.content.domain.enums.ContentStatus;
import com.ltz.content.domain.enums.ContentType;

/**
 * İçerik görünümü. Metin ({@code title}, {@code summary}, {@code body}) {@code locale} alanındaki dildedir: istenen dilde
 * çeviri varsa o, yoksa içeriğin ilk dilindeki çeviri sunulur; {@code availableLocales} mevcut çevirileri listeler.
 * {@code publishedAt} taslakta null'dır. {@code removedAt}/{@code removalReason} yalnız moderatörce kaldırılmış (REMOVED)
 * içerikte dolu olur ve yalnız içeriğin yazarına gösterilir (başkası kaldırılmış içeriği göremez); karar veren yetkili dönmez.
 */
public record ContentResponse(
		UUID id,
		UUID authorId,
		ContentType type,
		ContentStatus status,
		String locale,
		String title,
		String summary,
		String body,
		List<ContentImageResponse> images,
		Instant createdAt,
		Instant updatedAt,
		UUID gameId,
		List<String> categories,
		List<ContentSourceResponse> sources,
		boolean featured,
		Instant publishedAt,
		List<String> availableLocales,
		Instant removedAt,
		String removalReason) {
}

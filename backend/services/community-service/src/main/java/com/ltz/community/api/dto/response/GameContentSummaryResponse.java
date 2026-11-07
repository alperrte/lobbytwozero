package com.ltz.community.api.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.ltz.community.domain.enums.GameContentType;

/**
 * Liste özeti (GAME-11). {@code excerpt} düz metindir (HTML değildir, istemci metin olarak göstermelidir); gövdenin
 * boşlukları tek boşluğa indirilmiş ilk 200 karakteridir, kesildiyse "…" ile biter. {@code publishedAt}
 * oluşturulma anıdır (taslak durumu yoktur; içerik oluşturulunca yayımlanır). {@code detailPath} gerçek detay yoludur.
 */
public record GameContentSummaryResponse(
		UUID id,
		UUID gameId,
		GameContentType type,
		String title,
		String excerpt,
		UUID authorId,
		Instant publishedAt,
		Instant updatedAt,
		String detailPath) {
}

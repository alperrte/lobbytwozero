package com.ltz.community.api.dto.response;

import java.util.List;
import java.util.UUID;

import com.ltz.community.domain.enums.GameContentType;

/**
 * Sayfalı liste (GAME-11). {@code totalElements}, çağıranın görünürlük kuralları (engelleme, gizli/silinmiş içerik)
 * uygulandıktan sonra {@code gameId} ve {@code type} filtresine uyan toplam kayıt sayısıdır. {@code type} filtre
 * verilmediyse null'dır.
 */
public record GameContentPageResponse(
		UUID gameId,
		GameContentType type,
		List<GameContentSummaryResponse> items,
		int page,
		int size,
		long totalElements) {
}

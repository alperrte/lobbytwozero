package com.ltz.event.api.dto.response;

import java.util.List;

/** Sayfalı etkinlik listesi (EVT-04). {@code page} 0'dan başlar. */
public record EventPageResponse(
		List<EventSummaryResponse> items,
		int page,
		int size,
		long totalItems,
		int totalPages,
		boolean hasNext) {
}

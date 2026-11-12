package com.ltz.event.api.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ltz.event.domain.enums.EventPlatform;
import com.ltz.event.domain.enums.EventStatus;
import com.ltz.event.domain.enums.EventType;

/**
 * Liste öğesi özeti (EVT-04). Uzun açıklama listede taşınmaz; ayrıntı için etkinlik detayı (EVT-03) kullanılır.
 * Katılım bilgileri (kapasite, katılımcı sayısı) gerçek veri kaynağı olmadığından yoktur (EVT-13, EVT-08).
 */
public record EventSummaryResponse(
		UUID id,
		UUID organizerId,
		EventType type,
		UUID gameId,
		List<EventPlatform> platforms,
		String title,
		Instant startsAt,
		Instant endsAt,
		EventStatus status) {
}

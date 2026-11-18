package com.ltz.event.api.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ltz.event.domain.enums.EventPlatform;
import com.ltz.event.domain.enums.EventStatus;
import com.ltz.event.domain.enums.EventType;
import com.ltz.event.domain.enums.ParticipationStatus;

/**
 * Liste öğesi özeti (EVT-04). Uzun açıklama listede taşınmaz; ayrıntı için etkinlik detayı (EVT-03) kullanılır.
 * Katılım alanları (kapasite, katılımcı sayısı, kalan kontenjan, izleyici durumu, katılabilirlik) detay yanıtıyla aynı anlamdadır;
 * Recommendation gibi tüketiciler dolu/kapalı/zaten katılınmış etkinlikleri {@code joinable} ile ayıklar. Liste kullanıcıya
 * özeldir ({@code Cache-Control: private}).
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
		EventStatus status,
		Integer capacity,
		int participantCount,
		Integer remainingCapacity,
		ParticipationStatus viewerParticipation,
		boolean joinable) {
}

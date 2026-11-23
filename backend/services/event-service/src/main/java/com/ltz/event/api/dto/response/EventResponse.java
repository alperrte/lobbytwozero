package com.ltz.event.api.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ltz.event.domain.enums.EventPlatform;
import com.ltz.event.domain.enums.EventStatus;
import com.ltz.event.domain.enums.EventType;
import com.ltz.event.domain.enums.ParticipationStatus;

/**
 * Etkinlik detayı (EVT-03). Katılım alanları gerçek DB değerleridir: {@code capacity} null = sınırsız,
 * {@code remainingCapacity} sınırsızda null; {@code viewerParticipation} isteği yapan kullanıcının geçerli katılım durumudur
 * (yoksa null); {@code joinable} kullanıcının bu etkinliğe şu an katılabileceğini söyler (açık, dolu değil, kendi etkinliği
 * değil, zaten katılmamış). Yanıt kullanıcıya özeldir ({@code Cache-Control: private}). {@code organizerId} hesabı silinmiş
 * düzenleyicide null olur (KD-3).
 */
public record EventResponse(
		UUID id,
		UUID organizerId,
		EventType type,
		UUID gameId,
		List<EventPlatform> platforms,
		String title,
		String description,
		Instant startsAt,
		Instant endsAt,
		EventStatus status,
		Instant createdAt,
		Instant updatedAt,
		Integer capacity,
		int participantCount,
		Integer remainingCapacity,
		ParticipationStatus viewerParticipation,
		boolean joinable) {
}

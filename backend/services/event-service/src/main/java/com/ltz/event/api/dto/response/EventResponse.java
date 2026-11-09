package com.ltz.event.api.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.ltz.event.domain.enums.EventStatus;
import com.ltz.event.domain.enums.EventType;

public record EventResponse(
		UUID id,
		UUID organizerId,
		EventType type,
		UUID gameId,
		String title,
		String description,
		Instant startsAt,
		Instant endsAt,
		EventStatus status,
		Instant createdAt,
		Instant updatedAt) {
}

package com.ltz.event.api.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.ltz.event.domain.enums.ParticipationStatus;

/** Kullanıcının kendi katılım kaydı (EVT-08/09). Başka kullanıcıların bilgisi taşınmaz. */
public record ParticipationResponse(
		UUID id,
		UUID eventId,
		ParticipationStatus status,
		Instant joinedAt,
		Instant cancelledAt) {
}

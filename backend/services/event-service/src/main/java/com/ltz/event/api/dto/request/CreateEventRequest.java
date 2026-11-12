package com.ltz.event.api.dto.request;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ltz.event.domain.entity.Event;
import com.ltz.event.domain.enums.EventPlatform;
import com.ltz.event.domain.enums.EventType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Etkinlik oluşturma isteği (EVT-01). Sahip bilgisi (organizerId) kasıtlı olarak yoktur: etkinlik sahibi yalnız
 * doğrulanmış token'dan alınır. Bilinmeyen alanlar (ör. organizerId, status) {@code fail-on-unknown-properties} ile
 * reddedilir. {@code gameId} isteğe bağlıdır (EVT-17); verilirse Game Service'te yayında bir oyun olmalıdır. Zamanlar UTC ISO-8601 ({@code 2026-12-01T18:00:00Z}) olarak verilir.
 */
public record CreateEventRequest(
		@NotNull
		EventType type,
		@NotBlank
		@Size(max = Event.TITLE_MAX_LENGTH)
		@Pattern(regexp = Event.TITLE_ALLOWED_CHARACTERS, message = "must not contain control characters")
		String title,
		@NotBlank
		@Size(max = Event.DESCRIPTION_MAX_LENGTH)
		@Pattern(regexp = Event.DESCRIPTION_ALLOWED_CHARACTERS, message = "must not contain control characters")
		String description,
		@NotNull
		Instant startsAt,
		@NotNull
		Instant endsAt,
		UUID gameId,
		@Size(max = CreateEventRequest.MAX_PLATFORMS)
		List<EventPlatform> platforms) {

	/** Bir etkinlikte en çok kaç platform seçilebilir (yinelenenler birleştirilir; sözlük 6 değerdir). */
	public static final int MAX_PLATFORMS = 6;
}

package com.ltz.community.api.dto.request;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Servisler arası olay zarfı (docs/architecture.md §6.2): {@code eventId} tekilleştirme anahtarıdır; {@code payload}
 * olay türüne göre ayrıca doğrulanır. Zarfta tanımsız alan reddedilir.
 */
public record InboundEventRequest(
		@NotNull UUID eventId,
		@NotBlank @Size(max = 128) @Pattern(regexp = "[A-Za-z0-9._-]+", message = "must be a plain event name") String eventType,
		@NotNull @Min(1) Integer eventVersion,
		@NotNull Instant occurredAt,
		@NotBlank @Size(max = 64) @Pattern(regexp = "[a-z0-9-]+", message = "must be a service name") String producer,
		@NotNull UUID aggregateId,
		@NotNull Map<String, Object> payload) {
}

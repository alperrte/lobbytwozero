package com.ltz.user.api.dto.request;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Olay zarfı (docs/architecture.md §6.2). Zarfta tanımsız alan reddedilir. Yük yalnız ilgili olay türünün alanlarını
 * taşır; kişisel veri (ör. e-posta) olaylara konmaz.
 */
public record EventEnvelopeRequest(
        @NotNull UUID eventId,
        @NotBlank @Size(max = 128) String eventType,
        @NotNull @Positive Integer eventVersion,
        @NotNull Instant occurredAt,
        @NotBlank @Size(max = 64) String producer,
        @NotNull UUID aggregateId,
        @NotNull @Size(max = 32) Map<String, Object> payload) {
}

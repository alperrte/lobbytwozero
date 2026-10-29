package com.ltz.auth.application.port;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Servisler arası olay zarfı ({@code docs/architecture.md} §6.2). {@code eventId} tekilleştirme anahtarıdır ve bütün
 * hedeflerde aynıdır. Payload kişisel veri taşımaz: hesap kimliği ve en fazla bir değer (durum veya kullanıcı adı).
 */
public record AuthEventEnvelope(UUID eventId, String eventType, int eventVersion, Instant occurredAt, String producer,
                                UUID aggregateId, Map<String, Object> payload) {
}

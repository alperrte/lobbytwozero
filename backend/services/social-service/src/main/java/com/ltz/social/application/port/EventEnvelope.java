package com.ltz.social.application.port;

import java.time.Instant;
import java.util.UUID;

/**
 * Servisler arası olay zarfı (docs/architecture.md §6.2): tüketici {@code eventId} ile tekilleştirir.
 * {@code payload}, olay türüne özgü JSON metnidir ve yalnız kimlik/enum taşır.
 */
public record EventEnvelope(UUID eventId, String eventType, int eventVersion, Instant occurredAt, String producer,
                            UUID aggregateId, String payload) {
}

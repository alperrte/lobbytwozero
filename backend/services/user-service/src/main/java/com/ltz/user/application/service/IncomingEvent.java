package com.ltz.user.application.service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Servisler arası olay zarfı (docs/architecture.md §6.2). Yük, türe göre {@link EventIngestService} içinde doğrulanır. */
public record IncomingEvent(UUID eventId, String eventType, int eventVersion, Instant occurredAt, String producer,
                            UUID aggregateId, Map<String, Object> payload) {
}

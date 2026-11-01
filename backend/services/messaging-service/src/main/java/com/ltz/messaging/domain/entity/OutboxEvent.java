package com.ltz.messaging.domain.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Teslim edilmeyi bekleyen olay (docs/architecture.md §6.2). İş verisiyle aynı transaction'da {@code PENDING} olarak
 * yazılır; olay kaybı ve hayalet olay olmaz. Teslim (relay) alanları ({@code attempts}, {@code sent_at}) DB
 * varsayılanlarıyla başlar; Messaging'in henüz teslim hedefi olmadığından relay yoktur.
 */
@Entity
@Table(name = "outbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    static final String PENDING = "PENDING";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, updatable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, updatable = false, length = 128)
    private String eventType;

    @Column(name = "event_version", nullable = false, updatable = false)
    private int eventVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    public static OutboxEvent pending(String aggregateType, UUID aggregateId, String eventType, int eventVersion,
                                      String payloadJson, Instant now) {
        OutboxEvent event = new OutboxEvent();
        event.aggregateType = aggregateType;
        event.aggregateId = aggregateId;
        event.eventType = eventType;
        event.eventVersion = eventVersion;
        event.payload = payloadJson;
        event.occurredAt = now;
        event.status = PENDING;
        event.nextAttemptAt = now;
        return event;
    }
}

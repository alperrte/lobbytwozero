package com.ltz.social.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.social.application.port.EventEnvelope;
import com.ltz.social.domain.entity.OutboxEvent;
import com.ltz.social.infrastructure.config.OutboxProperties;
import com.ltz.social.infrastructure.persistence.repository.OutboxEventRepository;

import lombok.RequiredArgsConstructor;

/**
 * Outbox kayıtlarının kısa transaction'larla yönetimi. Uzak teslim bu transaction'ların DIŞINDA yapılır
 * ({@link OutboxRelay}); kilit tutarken ağ çağrısı yapılmaz.
 */
@Service
@RequiredArgsConstructor
public class OutboxEventStore {

    static final String PRODUCER = "social-service";

    private final OutboxEventRepository outbox;
    private final OutboxProperties properties;
    private final Clock clock;

    /** Zamanı gelmiş olayları kilitler, kira süresi kadar öteler ve teslim zarfı olarak döner. */
    @Transactional
    public List<EventEnvelope> claimDue() {
        Instant now = now();
        List<OutboxEvent> due = outbox.lockDue(now, properties.batchSize());
        due.forEach(event -> event.lease(now, properties.lease()));
        return due.stream().map(OutboxEventStore::toEnvelope).toList();
    }

    @Transactional
    public void markSent(UUID eventId) {
        outbox.findById(eventId).ifPresent(event -> event.markSent(now()));
    }

    @Transactional
    public void markFailed(UUID eventId) {
        outbox.findById(eventId).ifPresent(event -> event.markAttemptFailed(now(), properties.maxAttempts(),
                properties.baseBackoff(), properties.maxBackoff()));
    }

    private static EventEnvelope toEnvelope(OutboxEvent event) {
        return new EventEnvelope(event.getId(), event.getEventType(), event.getEventVersion(), event.getOccurredAt(),
                PRODUCER, event.getAggregateId(), event.getPayload());
    }

    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}

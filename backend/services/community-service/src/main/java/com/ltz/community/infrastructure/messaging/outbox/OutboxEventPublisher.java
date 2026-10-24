package com.ltz.community.infrastructure.messaging.outbox;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.community.application.port.DomainEventPublisher;

import tools.jackson.databind.json.JsonMapper;

/**
 * {@link DomainEventPublisher}'ın outbox uygulaması: olayı {@code outbox_events} tablosuna PENDING olarak yazar.
 * {@code MANDATORY}: çağıranın transaction'ına katılır; transaction yoksa olay yazılmaz ve hata fırlatılır, böylece
 * "iş verisi yazıldı ama olay kayboldu" durumu oluşamaz.
 */
@Component
public class OutboxEventPublisher implements DomainEventPublisher {

	static final int EVENT_VERSION = 1;

	private final JdbcClient jdbc;
	private final Clock clock;
	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	public OutboxEventPublisher(JdbcClient jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public void publish(String aggregateType, UUID aggregateId, String eventType, Map<String, Object> payload) {
		Instant now = Instant.now(clock);
		jdbc.sql("""
				insert into outbox_events (id, aggregate_type, aggregate_id, event_type, event_version, payload, occurred_at,
				                           status, attempts, next_attempt_at)
				values (:id, :aggregateType, :aggregateId, :eventType, :eventVersion, cast(:payload as jsonb), :occurredAt,
				        'PENDING', 0, :occurredAt)
				""")
				.param("id", UUID.randomUUID())
				.param("aggregateType", aggregateType)
				.param("aggregateId", aggregateId)
				.param("eventType", eventType)
				.param("eventVersion", EVENT_VERSION)
				.param("payload", jsonMapper.writeValueAsString(payload))
				.param("occurredAt", now.atOffset(ZoneOffset.UTC))
				.update();
	}
}

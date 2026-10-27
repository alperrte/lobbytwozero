package com.ltz.community.infrastructure.messaging.inbox;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.community.application.port.InboxPort;

/**
 * {@link InboxPort}'un {@code inbox_events} uygulaması. {@code event_id} birincil anahtardır; {@code on conflict do
 * nothing} sayesinde aynı olayın eşzamanlı ya da tekrarlanan teslimi yalnız bir kez "ilk" sayılır. {@code MANDATORY}:
 * kayıt, olayın iş etkisiyle aynı transaction'da yazılır; iş etkisi geri alınırsa kayıt da geri alınır.
 */
@Component
public class InboxEventStore implements InboxPort {

	private final JdbcClient jdbc;
	private final Clock clock;

	public InboxEventStore(JdbcClient jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public boolean markProcessed(UUID eventId, String eventType, String producer) {
		return jdbc.sql("""
				insert into inbox_events (event_id, event_type, producer, processed_at)
				values (:eventId, :eventType, :producer, :processedAt)
				on conflict (event_id) do nothing
				""")
				.param("eventId", eventId)
				.param("eventType", eventType)
				.param("producer", producer)
				.param("processedAt", clock.instant().atOffset(ZoneOffset.UTC))
				.update() == 1;
	}
}

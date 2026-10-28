package com.ltz.community.infrastructure.messaging.outbox;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ltz.community.application.port.EventDeliveryPort;
import com.ltz.community.application.port.EventDeliveryPort.Envelope;
import com.ltz.community.application.port.EventDeliveryPort.Target;
import com.ltz.community.infrastructure.messaging.outbox.OutboxStore.OutboxRecord;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Bekleyen outbox olaylarını bir parti halinde teslim eder. Başarılı olay SENT olur; başarısız olay üstel geri çekilmeyle
 * (taban süre × 2^deneme, en çok bir saat) yeniden denenir ve en çok {@code maxAttempts} denemeden sonra FAILED olur.
 * Tüketici {@code eventId} ile tekilleştirdiğinden aynı olayın tekrar teslimi güvenlidir. Log'lara yük yazılmaz.
 */
@Component
public class OutboxDispatcher {

	private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);
	private static final String PRODUCER = "community-service";
	private static final Duration MAX_BACKOFF = Duration.ofHours(1);
	private static final Duration SENT_RETENTION = Duration.ofDays(14);
	private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {
	};

	private final OutboxStore store;
	private final EventDeliveryPort delivery;
	private final Clock clock;
	private final int batchSize;
	private final int maxAttempts;
	private final Duration baseBackoff;
	private final Duration lease;
	private final JsonMapper jsonMapper = JsonMapper.builder().build();
	private Instant lastPurge = Instant.EPOCH;

	public OutboxDispatcher(OutboxStore store, EventDeliveryPort delivery, Clock clock,
			@Value("${ltz.outbox.relay.batch-size:50}") int batchSize,
			@Value("${ltz.outbox.relay.max-attempts:10}") int maxAttempts,
			@Value("${ltz.outbox.relay.base-backoff-seconds:10}") long baseBackoffSeconds,
			@Value("${ltz.outbox.relay.lease-seconds:60}") long leaseSeconds) {
		this.store = store;
		this.delivery = delivery;
		this.clock = clock;
		this.batchSize = batchSize;
		this.maxAttempts = maxAttempts;
		this.baseBackoff = Duration.ofSeconds(baseBackoffSeconds);
		this.lease = Duration.ofSeconds(leaseSeconds);
	}

	/** Bir parti işler; teslim edilen olay sayısını döndürür. */
	public int dispatchBatch() {
		Instant now = Instant.now(clock);
		List<OutboxRecord> claimed = store.claimDue(now, batchSize, now.plus(lease));
		int delivered = 0;
		for (OutboxRecord event : claimed) {
			if (deliverOne(event)) {
				delivered++;
			}
		}
		purgeOldSentEvents(now);
		return delivered;
	}

	private boolean deliverOne(OutboxRecord event) {
		Instant now = Instant.now(clock);
		try {
			delivery.deliver(targetOf(event.eventType()), envelope(event));
			store.markSent(event.id(), now);
			return true;
		} catch (RuntimeException exception) {
			int attempts = event.attempts() + 1;
			boolean giveUp = attempts >= maxAttempts || exception instanceof UnroutableEventException;
			store.markFailedAttempt(event.id(), attempts, giveUp ? null : now.plus(backoff(attempts)));
			log.warn("Outbox event {} ({}) delivery failed on attempt {}: {}", event.id(), event.eventType(), attempts,
					exception.getClass().getSimpleName());
			return false;
		}
	}

	private Duration backoff(int attempts) {
		long factor = 1L << Math.min(attempts - 1, 20);
		Duration delay = baseBackoff.multipliedBy(factor);
		return delay.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : delay;
	}

	private Envelope envelope(OutboxRecord event) {
		try {
			return new Envelope(event.id(), event.eventType(), event.eventVersion(), event.occurredAt(), PRODUCER,
					event.aggregateId(), jsonMapper.readValue(event.payloadJson(), PAYLOAD_TYPE));
		} catch (JacksonException exception) {
			throw new UnroutableEventException("Outbox payload is not valid JSON", exception);
		}
	}

	private static Target targetOf(String eventType) {
		return switch (eventType) {
			case "ContentReported" -> Target.ADMIN;
			case "CommentCreated", "PostLiked", "PostReposted" -> Target.NOTIFICATION;
			default -> throw new UnroutableEventException("No delivery target for event type " + eventType, null);
		};
	}

	/** Saatte en çok bir kez, teslim edilmiş eski olayları temizler. */
	private void purgeOldSentEvents(Instant now) {
		if (Duration.between(lastPurge, now).compareTo(Duration.ofHours(1)) < 0) {
			return;
		}
		lastPurge = now;
		int purged = store.purgeSentBefore(now.minus(SENT_RETENTION));
		if (purged > 0) {
			log.info("Purged {} delivered outbox events", purged);
		}
	}

	/** Yeniden denemenin işe yaramayacağı kalıcı hata (bilinmeyen olay türü, bozuk yük). */
	static final class UnroutableEventException extends RuntimeException {

		UnroutableEventException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}

package com.ltz.community.application.port;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Outbox olaylarını tüketici servislerin iç endpoint'ine ({@code POST /internal/events}) iletir. */
public interface EventDeliveryPort {

	/** Olayın gönderileceği servis. */
	enum Target {
		ADMIN,
		NOTIFICATION
	}

	/**
	 * Servisler arası olay zarfı (docs/architecture.md §6.2). {@code eventId} outbox kaydının kimliğidir; tüketici bununla
	 * tekilleştirir, bu yüzden aynı olayın yeniden teslimi güvenlidir.
	 */
	record Envelope(UUID eventId, String eventType, int eventVersion, Instant occurredAt, String producer,
			UUID aggregateId, Map<String, Object> payload) {
	}

	/**
	 * @throws RuntimeException teslim başarısızsa (ağ hatası, zaman aşımı, 4xx/5xx); çağıran yeniden dener
	 */
	void deliver(Target target, Envelope envelope);
}

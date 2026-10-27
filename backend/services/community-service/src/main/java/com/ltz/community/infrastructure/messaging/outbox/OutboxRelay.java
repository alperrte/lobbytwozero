package com.ltz.community.infrastructure.messaging.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Outbox relay'ini zamanlar. Alıcı servislerin {@code /internal/events} sözleşmesi ve servisler arası kimlik modeli
 * netleşene kadar varsayılan olarak KAPALIDIR: olaylar outbox'ta PENDING bekler ve veri kaybolmaz. Açmak için
 * {@code ltz.outbox.relay.enabled=true} verilir (ortam değişkeni karşılığı {@code LTZ_OUTBOX_RELAY_ENABLED}).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "ltz.outbox.relay", name = "enabled", havingValue = "true")
public class OutboxRelay {

	private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

	private final OutboxDispatcher dispatcher;

	public OutboxRelay(OutboxDispatcher dispatcher) {
		this.dispatcher = dispatcher;
	}

	@Scheduled(fixedDelayString = "${ltz.outbox.relay.interval-ms:5000}")
	public void relay() {
		try {
			dispatcher.dispatchBatch();
		} catch (RuntimeException exception) {
			// Bir parti hatası zamanlayıcıyı durdurmaz; sonraki turda tekrar denenir.
			log.error("Outbox relay batch failed: {}", exception.getClass().getSimpleName());
		}
	}
}

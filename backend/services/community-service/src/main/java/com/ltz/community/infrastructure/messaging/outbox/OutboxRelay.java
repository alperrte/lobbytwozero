package com.ltz.community.infrastructure.messaging.outbox;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.ltz.community.application.port.ServiceTokenPort;

/**
 * Outbox relay'ini zamanlar. Alıcı servislerin {@code /internal/events} uçları hazır olana kadar varsayılan olarak
 * KAPALIDIR: olaylar outbox'ta PENDING bekler ve veri kaybolmaz. Açmak için {@code ltz.outbox.relay.enabled=true}
 * verilir (ortam değişkeni karşılığı {@code LTZ_OUTBOX_RELAY_ENABLED}). Açıkken de Auth istemci gizli anahtarı
 * tanımlı değilse hiçbir olay denenmez; token alınamayan çağrılar olayların deneme hakkını boşuna tüketirdi.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "ltz.outbox.relay", name = "enabled", havingValue = "true")
public class OutboxRelay {

	private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

	private final OutboxDispatcher dispatcher;
	private final ServiceTokenPort serviceTokens;
	private final AtomicBoolean warnedNotConfigured = new AtomicBoolean();

	public OutboxRelay(OutboxDispatcher dispatcher, ServiceTokenPort serviceTokens) {
		this.dispatcher = dispatcher;
		this.serviceTokens = serviceTokens;
	}

	@Scheduled(fixedDelayString = "${ltz.outbox.relay.interval-ms:5000}")
	public void relay() {
		if (!serviceTokens.isConfigured()) {
			if (warnedNotConfigured.compareAndSet(false, true)) {
				log.warn("Outbox relay is enabled but the Auth client secret is not configured; events stay PENDING.");
			}
			return;
		}
		warnedNotConfigured.set(false);
		try {
			dispatcher.dispatchBatch();
		} catch (RuntimeException exception) {
			// Bir parti hatası zamanlayıcıyı durdurmaz; sonraki turda tekrar denenir.
			log.error("Outbox relay batch failed: {}", exception.getClass().getSimpleName());
		}
	}
}

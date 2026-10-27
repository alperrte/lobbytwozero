package com.ltz.community.infrastructure.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.ltz.community.application.port.EventDeliveryPort;

/**
 * Olayları admin-service ve notification-service iç endpoint'ine RestClient ile iletir. Zaman aşımı/yönlendirme
 * ayarları {@code spring.http.clients.*} ile gelir; yeniden deneme outbox relay'indedir. Servisler arası kimlik modeli
 * (ADR) netleşene kadar istekte kimlik başlığı gönderilmez; tüketici fail-closed olduğundan teslim şimdilik doğrulanamaz.
 */
@Component
public class EventDeliveryClient implements EventDeliveryPort {

	static final String EVENTS_PATH = "/internal/events";

	private final RestClient adminClient;
	private final RestClient notificationClient;

	public EventDeliveryClient(RestClient.Builder builder,
			@Value("${ltz.clients.admin-service.url}") String adminUrl,
			@Value("${ltz.clients.notification-service.url}") String notificationUrl) {
		this.adminClient = builder.clone().baseUrl(adminUrl).build();
		this.notificationClient = builder.clone().baseUrl(notificationUrl).build();
	}

	@Override
	public void deliver(Target target, Envelope envelope) {
		RestClient client = target == Target.ADMIN ? adminClient : notificationClient;
		client.post()
				.uri(EVENTS_PATH)
				.contentType(MediaType.APPLICATION_JSON)
				.body(envelope)
				.retrieve()
				.toBodilessEntity();
	}
}

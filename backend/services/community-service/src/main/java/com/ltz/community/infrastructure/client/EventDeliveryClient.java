package com.ltz.community.infrastructure.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.ltz.community.application.port.EventDeliveryPort;
import com.ltz.community.application.port.ServiceTokenPort;

/**
 * Olayları admin-service ve notification-service iç endpoint'ine RestClient ile iletir. Zaman aşımı/yönlendirme
 * ayarları {@code spring.http.clients.*} ile gelir; yeniden deneme outbox relay'indedir. Her çağrı, Auth'tan alınan
 * hedefe bağlı servis token'ıyla ({@code Authorization: Bearer}, {@code scope=events:ingest}) yapılır.
 */
@Component
public class EventDeliveryClient implements EventDeliveryPort {

	static final String EVENTS_PATH = "/internal/events";
	static final String ADMIN_AUDIENCE = "admin-service";
	static final String NOTIFICATION_AUDIENCE = "notification-service";

	private final RestClient adminClient;
	private final RestClient notificationClient;
	private final ServiceTokenPort serviceTokens;

	public EventDeliveryClient(RestClient.Builder builder, ServiceTokenPort serviceTokens,
			@Value("${ltz.clients.admin-service.url}") String adminUrl,
			@Value("${ltz.clients.notification-service.url}") String notificationUrl) {
		this.adminClient = builder.clone().baseUrl(adminUrl).build();
		this.notificationClient = builder.clone().baseUrl(notificationUrl).build();
		this.serviceTokens = serviceTokens;
	}

	@Override
	public void deliver(Target target, Envelope envelope) {
		boolean admin = target == Target.ADMIN;
		RestClient client = admin ? adminClient : notificationClient;
		// Her hedef için ayrı, hedefe bağlı (aud) token; alıcı aud/typ/scope/sub'ı doğrular.
		String token = serviceTokens.tokenFor(admin ? ADMIN_AUDIENCE : NOTIFICATION_AUDIENCE);
		client.post()
				.uri(EVENTS_PATH)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.body(envelope)
				.retrieve()
				.toBodilessEntity();
	}
}

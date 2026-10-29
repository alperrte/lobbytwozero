package com.ltz.community.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClientException;

import com.ltz.community.application.port.EventDeliveryPort.Envelope;
import com.ltz.community.application.port.EventDeliveryPort.Target;
import com.ltz.community.application.port.ServiceTokenPort;
import com.ltz.community.domain.exception.ServiceTokenUnavailableException;

@RestClientTest(EventDeliveryClient.class)
@TestPropertySource(properties = {
		"ltz.clients.admin-service.url=http://admin-service:8080",
		"ltz.clients.notification-service.url=http://notification-service:8080" })
class EventDeliveryClientTest {

	private static final UUID EVENT_ID = UUID.fromString("0198a3c2-1111-7d44-9a55-3f1c2b4d5e66");
	private static final UUID AGGREGATE_ID = UUID.fromString("0198a3c2-2222-7d44-9a55-3f1c2b4d5e66");

	@Autowired
	private EventDeliveryClient client;

	@Autowired
	private MockRestServiceServer server;

	@MockitoBean
	private ServiceTokenPort serviceTokens;

	@BeforeEach
	void tokens() {
		when(serviceTokens.tokenFor(anyString())).thenAnswer(call -> "token-for-" + call.getArgument(0));
	}

	private static Envelope envelope() {
		return new Envelope(EVENT_ID, "PostLiked", 1, Instant.parse("2026-10-21T10:00:00Z"), "community-service",
				AGGREGATE_ID, Map.of("postId", "p1"));
	}

	@Test
	void notificationEventsGoToTheNotificationServiceInternalEndpoint() {
		server.expect(requestTo("http://notification-service:8080/internal/events"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
				.andExpect(header("Authorization", "Bearer token-for-notification-service"))
				.andExpect(jsonPath("$.eventId").value(EVENT_ID.toString()))
				.andExpect(jsonPath("$.eventType").value("PostLiked"))
				.andExpect(jsonPath("$.eventVersion").value(1))
				.andExpect(jsonPath("$.producer").value("community-service"))
				.andExpect(jsonPath("$.aggregateId").value(AGGREGATE_ID.toString()))
				.andExpect(jsonPath("$.payload.postId").value("p1"))
				.andRespond(withSuccess());

		client.deliver(Target.NOTIFICATION, envelope());

		server.verify();
	}

	@Test
	void moderationReportsGoToTheAdminServiceInternalEndpoint() {
		server.expect(requestTo("http://admin-service:8080/internal/events"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("Authorization", "Bearer token-for-admin-service"))
				.andRespond(withSuccess());

		client.deliver(Target.ADMIN, envelope());

		server.verify();
		verify(serviceTokens).tokenFor("admin-service");
	}

	@Test
	void noRequestIsSentWhenTheServiceTokenCannotBeObtained() {
		when(serviceTokens.tokenFor("admin-service")).thenThrow(new ServiceTokenUnavailableException("no token"));

		Envelope envelope = envelope();
		assertThatThrownBy(() -> client.deliver(Target.ADMIN, envelope))
				.isInstanceOf(ServiceTokenUnavailableException.class);
		server.verify();
	}

	@Test
	void errorResponsesAreReportedSoTheRelayCanRetry() {
		server.expect(requestTo("http://admin-service:8080/internal/events")).andRespond(withServerError());
		server.expect(requestTo("http://admin-service:8080/internal/events")).andRespond(withStatus(HttpStatus.FORBIDDEN));

		Envelope envelope = envelope();
		assertThatThrownBy(() -> client.deliver(Target.ADMIN, envelope)).isInstanceOf(RestClientException.class);
		assertThatThrownBy(() -> client.deliver(Target.ADMIN, envelope)).isInstanceOf(RestClientException.class);
		server.verify();
	}
}

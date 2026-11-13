package com.ltz.messaging.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.ltz.messaging.application.port.EventDeliveryException;
import com.ltz.messaging.application.port.EventEnvelope;
import com.ltz.messaging.application.port.ServiceTokenPort;
import com.ltz.messaging.application.port.ServiceTokenUnavailableException;

import tools.jackson.databind.json.JsonMapper;

/** notification-service'e olay teslimi: uç, servis token'ı, zarf biçimi ve her hatada teslim sayılmama. */
class NotificationEventPublisherTest {

    private static final String EVENTS_URL = "http://notification-service:8080/internal/events";
    private static final String PAYLOAD_SECRET = "payload-marker";

    private final UUID eventId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final UUID recipient = UUID.randomUUID();

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private String issuedAudience;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
    }

    private NotificationEventPublisher publisher(ServiceTokenPort tokens) {
        return new NotificationEventPublisher(builder, tokens, JsonMapper.builder().build(),
                "http://notification-service:8080");
    }

    private NotificationEventPublisher publisher() {
        return publisher(audience -> {
            issuedAudience = audience;
            return "svc-token";
        });
    }

    private EventEnvelope envelope(String type) {
        String payload = "{\"messageId\":\"%s\",\"conversationId\":\"%s\",\"conversationType\":\"DIRECT\",\"seq\":3,"
                + "\"senderId\":\"%s\",\"recipientIds\":[\"%s\"],\"note\":\"%s\"}";
        return new EventEnvelope(eventId, type, 1, Instant.parse("2026-11-13T10:00:00Z"), "messaging-service",
                conversationId, payload.formatted(UUID.randomUUID(), conversationId, UUID.randomUUID(), recipient,
                        PAYLOAD_SECRET));
    }

    @Test
    void messageSentOlayiniServisTokeniylaZarfOlarakIletir() {
        server.expect(once(), requestTo(EVENTS_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer svc-token"))
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.eventType").value("MessageSent"))
                .andExpect(jsonPath("$.eventVersion").value(1))
                .andExpect(jsonPath("$.producer").value("messaging-service"))
                .andExpect(jsonPath("$.aggregateId").value(conversationId.toString()))
                .andExpect(jsonPath("$.occurredAt").exists())
                .andExpect(jsonPath("$.payload.seq").value(3))
                .andExpect(jsonPath("$.payload.recipientIds[0]").value(recipient.toString()))
                .andRespond(withSuccess("{\"outcome\":\"PROCESSED\"}", MediaType.APPLICATION_JSON));

        publisher().publish(envelope("MessageSent"));

        server.verify();
        assertThat(issuedAudience).isEqualTo("notification-service");
    }

    @Test
    void islenmeyenOlayTuruIcinHicCagriYapmaz() {
        NotificationEventPublisher publisher = publisher();

        assertThatThrownBy(() -> publisher.publish(envelope("SomethingElse")))
                .isInstanceOf(EventDeliveryException.class)
                .hasMessageContaining("No delivery target");
        server.verify();
        assertThat(issuedAudience).isNull();
    }

    @Test
    void tokenAlinamazsaTeslimEdilmemisSayilir() {
        NotificationEventPublisher publisher = publisher(audience -> {
            throw new ServiceTokenUnavailableException("no secret");
        });

        assertThatThrownBy(() -> publisher.publish(envelope("MessageSent")))
                .isInstanceOf(EventDeliveryException.class)
                .hasMessage("Service token is unavailable");
        server.verify();
    }

    @Test
    void sunucuHatasindaTeslimEdilmemisSayilirIletideYukYok() {
        server.expect(once(), requestTo(EVENTS_URL)).andRespond(withServerError().body(PAYLOAD_SECRET));
        NotificationEventPublisher publisher = publisher();

        assertThatThrownBy(() -> publisher.publish(envelope("MessageSent")))
                .isInstanceOf(EventDeliveryException.class)
                .hasMessageNotContaining(PAYLOAD_SECRET)
                .hasMessageNotContaining("svc-token");
    }

    @Test
    void yonlendirmeTeslimSayilmaz() {
        server.expect(once(), requestTo(EVENTS_URL))
                .andRespond(withStatus(HttpStatus.FOUND).location(java.net.URI.create("http://elsewhere/")));
        NotificationEventPublisher publisher = publisher();

        assertThatThrownBy(() -> publisher.publish(envelope("MessageSent")))
                .isInstanceOf(EventDeliveryException.class)
                .hasMessageContaining("HTTP 302");
    }

    @Test
    void yetkiReddiTeslimSayilmaz() {
        server.expect(once(), requestTo(EVENTS_URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));
        NotificationEventPublisher publisher = publisher();

        assertThatThrownBy(() -> publisher.publish(envelope("MessageSent")))
                .isInstanceOf(EventDeliveryException.class);
    }

    @Test
    void servisKimligiYoksaHazirDegildir() {
        ServiceTokenPort unconfigured = new ServiceTokenPort() {
            @Override
            public String tokenFor(String audience) {
                throw new ServiceTokenUnavailableException("no secret");
            }

            @Override
            public boolean isConfigured() {
                return false;
            }
        };

        assertThat(publisher(unconfigured).isReady()).isFalse();
        assertThat(publisher().isReady()).isTrue();
    }

    @Test
    void hedefSecilmemisYayinciHicHazirOlmaz() {
        UnavailableEventPublisher unavailable = new UnavailableEventPublisher();
        EventEnvelope event = envelope("MessageSent");

        assertThat(unavailable.isReady()).isFalse();
        assertThatThrownBy(() -> unavailable.publish(event)).isInstanceOf(EventDeliveryException.class);
    }
}

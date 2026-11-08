package com.ltz.social.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.ltz.social.application.port.EventDeliveryException;
import com.ltz.social.application.port.EventEnvelope;
import com.ltz.social.infrastructure.config.ServiceClientProperties;

import tools.jackson.databind.json.JsonMapper;

/**
 * Arkadaşlık ve engel olayları user-service'in {@code POST /internal/events} ucuna Auth servis token'ıyla iletilir
 * (aud=user-service, events:ingest); user-service bunlarla arkadaş/engel projeksiyonunu tutar.
 */
class UserEventPublisherTest {

    private static final String EVENTS_URL = "http://user-service:8080/internal/events";
    private static final UUID EVENT_ID = UUID.fromString("0198a3c2-1111-7d44-9a55-3f1c2b4d5e66");
    private static final UUID AGGREGATE_ID = UUID.fromString("0198a3c2-2222-7d44-9a55-3f1c2b4d5e66");
    private static final UUID LOW = UUID.fromString("0198a3c2-3333-7d44-9a55-3f1c2b4d5e66");
    private static final UUID HIGH = UUID.fromString("0198a3c2-4444-7d44-9a55-3f1c2b4d5e66");

    private StubServiceTokens tokens;
    private MockRestServiceServer server;
    private UserEventPublisher publisher;

    @BeforeEach
    void setUp() {
        tokens = new StubServiceTokens();
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        publisher = new UserEventPublisher(builder, tokens, JsonMapper.builder().build(), new ServiceClientProperties(
                "http://auth-service:8080", "/internal/v1/service-tokens", "social-service", "secret",
                "http://community-service:8080", "http://user-service:8080"));
    }

    private static EventEnvelope envelope(String type, String payload) {
        return new EventEnvelope(EVENT_ID, type, 1, Instant.parse("2026-11-09T10:00:00Z"), "social-service",
                AGGREGATE_ID, payload);
    }

    @Test
    void friendshipChangedUserServisineTokenlaIletilirPayloadIcIceNesne() {
        server.expect(once(), requestTo(EVENTS_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + StubServiceTokens.TOKEN))
                .andExpect(jsonPath("$.eventId").value(EVENT_ID.toString()))
                .andExpect(jsonPath("$.eventType").value("FriendshipChanged"))
                .andExpect(jsonPath("$.producer").value("social-service"))
                .andExpect(jsonPath("$.aggregateId").value(AGGREGATE_ID.toString()))
                .andExpect(jsonPath("$.payload.userLowId").value(LOW.toString()))
                .andExpect(jsonPath("$.payload.userHighId").value(HIGH.toString()))
                .andExpect(jsonPath("$.payload.change").value("ADDED"))
                .andRespond(withSuccess());

        publisher.publish(envelope("FriendshipChanged",
                "{\"userLowId\":\"%s\",\"userHighId\":\"%s\",\"change\":\"ADDED\"}".formatted(LOW, HIGH)));

        server.verify();
        assertThat(tokens.requestedAudiences).containsExactly("user-service");
    }

    @Test
    void engelOlaylariDaUserServisineIletilir() {
        String payload = "{\"blockerId\":\"%s\",\"blockedId\":\"%s\"}".formatted(LOW, HIGH);
        server.expect(once(), requestTo(EVENTS_URL)).andExpect(jsonPath("$.eventType").value("UserBlocked"))
                .andRespond(withSuccess());
        server.expect(once(), requestTo(EVENTS_URL)).andExpect(jsonPath("$.eventType").value("UserUnblocked"))
                .andRespond(withSuccess());

        publisher.publish(envelope("UserBlocked", payload));
        publisher.publish(envelope("UserUnblocked", payload));

        server.verify();
    }

    @Test
    void userServisiIlgilenmedigiTurleriAlmaz() {
        for (String type : List.of("FriendRequestSent", "FriendRequestAccepted")) {
            assertThat(publisher.handles(type)).isFalse();
            assertThatThrownBy(() -> publisher.publish(envelope(type, "{}")))
                    .isInstanceOf(EventDeliveryException.class)
                    .hasMessageContaining("No delivery target");
        }

        server.verify();
        assertThat(tokens.requestedAudiences).isEmpty();
    }

    @Test
    void userServisiHataDonerseTeslimEdilemezIletideGovdeYok() {
        server.expect(once(), requestTo(EVENTS_URL)).andRespond(withServerError().body("secret-body-detail"));

        assertThatThrownBy(() -> publisher.publish(envelope("FriendshipChanged",
                "{\"userLowId\":\"%s\",\"userHighId\":\"%s\",\"change\":\"REMOVED\"}".formatted(LOW, HIGH))))
                .isInstanceOf(EventDeliveryException.class)
                .hasMessageNotContaining("secret-body-detail")
                .hasMessageNotContaining(StubServiceTokens.TOKEN);
    }
}

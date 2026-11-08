package com.ltz.social.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.ltz.social.application.port.EventDeliveryException;
import com.ltz.social.application.port.EventEnvelope;
import com.ltz.social.infrastructure.config.ServiceClientProperties;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Bir olay, türünü işleyen her hedefe iletilir. Hedeflerden biri bile alamazsa olay teslim edilmemiş sayılır ve relay
 * tekrar dener; alıcılar {@code eventId} ile tekilleştirdiğinden önceden alan hedef olayı ikinci kez uygulamaz.
 */
class FanOutEventPublisherTest {

    private static final String COMMUNITY_URL = "http://community-service:8080/internal/events";
    private static final String USER_URL = "http://user-service:8080/internal/events";
    private static final UUID A = UUID.fromString("0198a3c2-3333-7d44-9a55-3f1c2b4d5e66");
    private static final UUID B = UUID.fromString("0198a3c2-4444-7d44-9a55-3f1c2b4d5e66");

    private StubServiceTokens tokens;
    private MockRestServiceServer server;
    private FanOutEventPublisher publisher;

    @BeforeEach
    void setUp() {
        tokens = new StubServiceTokens();
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        ObjectMapper objectMapper = JsonMapper.builder().build();
        ServiceClientProperties properties = new ServiceClientProperties("http://auth-service:8080",
                "/internal/v1/service-tokens", "social-service", "secret", "http://community-service:8080",
                "http://user-service:8080");
        publisher = new FanOutEventPublisher(List.of(
                new CommunityEventPublisher(builder, tokens, objectMapper, properties),
                new UserEventPublisher(builder, tokens, objectMapper, properties)));
    }

    private static EventEnvelope envelope(String type, String payload) {
        return new EventEnvelope(UUID.randomUUID(), type, 1, Instant.parse("2026-11-09T10:00:00Z"), "social-service",
                UUID.randomUUID(), payload);
    }

    private static String blockPayload() {
        return "{\"blockerId\":\"%s\",\"blockedId\":\"%s\"}".formatted(A, B);
    }

    @Test
    void engelOlayiHemCommunityHemUserServisineGider() {
        server.expect(once(), requestTo(COMMUNITY_URL)).andRespond(withSuccess());
        server.expect(once(), requestTo(USER_URL)).andRespond(withSuccess());

        publisher.publish(envelope("UserBlocked", blockPayload()));

        server.verify();
        assertThat(tokens.requestedAudiences).containsExactly("community-service", "user-service");
    }

    @Test
    void arkadaslikDegisikligiYalnizUserServisineGider() {
        server.expect(once(), requestTo(USER_URL)).andRespond(withSuccess());

        publisher.publish(envelope("FriendshipChanged",
                "{\"userLowId\":\"%s\",\"userHighId\":\"%s\",\"change\":\"ADDED\"}".formatted(A, B)));

        server.verify();
        assertThat(tokens.requestedAudiences).containsExactly("user-service");
    }

    @Test
    void hicbirHedefinIslemedigiTurTeslimEdilemezHicbirCagriYapilmaz() {
        assertThatThrownBy(() -> publisher.publish(envelope("FriendRequestSent", "{}")))
                .isInstanceOf(EventDeliveryException.class)
                .hasMessageContaining("No delivery target");

        server.verify();
        assertThat(tokens.requestedAudiences).isEmpty();
    }

    @Test
    void hedeflerdenBiriDuserseOlayTeslimEdilmemisSayilir() {
        server.expect(once(), requestTo(COMMUNITY_URL)).andRespond(withSuccess());
        server.expect(once(), requestTo(USER_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> publisher.publish(envelope("UserUnblocked", blockPayload())))
                .isInstanceOf(EventDeliveryException.class);

        server.verify();
    }

    @Test
    void hazirlikTumHedeflerinHazirOlmasinaBagli() {
        assertThat(publisher.isReady()).isTrue();
        tokens.configured = false;
        assertThat(publisher.isReady()).isFalse();
    }
}

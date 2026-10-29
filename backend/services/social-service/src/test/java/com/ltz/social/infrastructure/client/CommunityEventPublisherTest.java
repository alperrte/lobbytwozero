package com.ltz.social.infrastructure.client;

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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.ltz.social.application.port.EventDeliveryException;
import com.ltz.social.application.port.EventEnvelope;
import com.ltz.social.application.port.ServiceTokenPort;
import com.ltz.social.domain.exception.ServiceTokenUnavailableException;
import com.ltz.social.infrastructure.config.ServiceClientProperties;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Engel olayları community-service'e Auth servis token'ıyla iletilir; diğer türler ve hatalar outbox'ta bekletilir. */
class CommunityEventPublisherTest {

    private static final String EVENTS_URL = "http://community-service:8080/internal/events";
    private static final String TOKEN = "service-token-value";
    private static final UUID EVENT_ID = UUID.fromString("0198a3c2-1111-7d44-9a55-3f1c2b4d5e66");
    private static final UUID AGGREGATE_ID = UUID.fromString("0198a3c2-2222-7d44-9a55-3f1c2b4d5e66");
    private static final UUID BLOCKER = UUID.fromString("0198a3c2-3333-7d44-9a55-3f1c2b4d5e66");
    private static final UUID BLOCKED = UUID.fromString("0198a3c2-4444-7d44-9a55-3f1c2b4d5e66");

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private StubTokens tokens;
    private MockRestServiceServer server;
    private CommunityEventPublisher publisher;

    @BeforeEach
    void setUp() {
        tokens = new StubTokens();
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        publisher = new CommunityEventPublisher(builder, tokens, objectMapper, new ServiceClientProperties(
                "http://auth-service:8080", "/internal/v1/service-tokens", "social-service", "secret",
                "http://community-service:8080"));
    }

    private static EventEnvelope envelope(String type) {
        String payload = "{\"blockerId\":\"%s\",\"blockedId\":\"%s\"}".formatted(BLOCKER, BLOCKED);
        return new EventEnvelope(EVENT_ID, type, 1, Instant.parse("2026-10-29T10:00:00Z"), "social-service",
                AGGREGATE_ID, payload);
    }

    @Test
    void userBlockedCommunityIcUcunaTokenlaIletilirPayloadIcIceNesne() {
        server.expect(once(), requestTo(EVENTS_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.eventId").value(EVENT_ID.toString()))
                .andExpect(jsonPath("$.eventType").value("UserBlocked"))
                .andExpect(jsonPath("$.eventVersion").value(1))
                .andExpect(jsonPath("$.occurredAt").value("2026-10-29T10:00:00Z"))
                .andExpect(jsonPath("$.producer").value("social-service"))
                .andExpect(jsonPath("$.aggregateId").value(AGGREGATE_ID.toString()))
                .andExpect(jsonPath("$.payload.blockerId").value(BLOCKER.toString()))
                .andExpect(jsonPath("$.payload.blockedId").value(BLOCKED.toString()))
                .andRespond(withSuccess());

        publisher.publish(envelope("UserBlocked"));

        server.verify();
        assertThat(tokens.requestedAudiences).containsExactly("community-service");
    }

    @Test
    void userUnblockedDeIletilir() {
        server.expect(once(), requestTo(EVENTS_URL)).andExpect(jsonPath("$.eventType").value("UserUnblocked"))
                .andRespond(withSuccess());

        publisher.publish(envelope("UserUnblocked"));

        server.verify();
    }

    @Test
    void hedefiOlmayanOlayTuruTeslimEdilemezHicbirCagriYapilmaz() {
        for (String type : List.of("FriendRequestSent", "FriendRequestAccepted", "FriendshipChanged")) {
            assertThatThrownBy(() -> publisher.publish(envelope(type)))
                    .isInstanceOf(EventDeliveryException.class)
                    .hasMessageContaining("No delivery target");
        }

        server.verify();
        assertThat(tokens.requestedAudiences).isEmpty();
    }

    @Test
    void tokenAlinamazsaTeslimEdilemezCagriYapilmazIletideTokenYok() {
        tokens.failing = true;

        assertThatThrownBy(() -> publisher.publish(envelope("UserBlocked")))
                .isInstanceOf(EventDeliveryException.class)
                .hasMessageNotContaining(TOKEN);

        server.verify();
    }

    @Test
    void communityHataDonerseTeslimEdilemezIletideTokenVeGovdeYok() {
        server.expect(once(), requestTo(EVENTS_URL)).andRespond(withServerError().body("secret-body-detail"));

        assertThatThrownBy(() -> publisher.publish(envelope("UserBlocked")))
                .isInstanceOf(EventDeliveryException.class)
                .hasMessageNotContaining(TOKEN)
                .hasMessageNotContaining("secret-body-detail");
    }

    @Test
    void reddedilenTokenIcin401DeTeslimEdilemez() {
        server.expect(once(), requestTo(EVENTS_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> publisher.publish(envelope("UserBlocked")))
                .isInstanceOf(EventDeliveryException.class);
    }

    @Test
    void yonlendirmeVeDigerBasariDisiYanitlarTeslimSayilmaz() {
        server.expect(once(), requestTo(EVENTS_URL))
                .andRespond(withStatus(HttpStatus.FOUND).header("Location", "http://evil.example/"));

        assertThatThrownBy(() -> publisher.publish(envelope("UserBlocked")))
                .isInstanceOf(EventDeliveryException.class);
    }

    @Test
    void bozukPayloadTeslimEdilemez() {
        EventEnvelope broken = new EventEnvelope(EVENT_ID, "UserBlocked", 1, Instant.now(), "social-service",
                AGGREGATE_ID, "{not json");

        assertThatThrownBy(() -> publisher.publish(broken)).isInstanceOf(EventDeliveryException.class);

        server.verify();
    }

    @Test
    void hazirlikGizliAnahtarinTanimliOlmasinaBagli() {
        assertThat(publisher.isReady()).isTrue();
        tokens.configured = false;
        assertThat(publisher.isReady()).isFalse();
    }

    /** Auth'a gitmeyen sahte token kaynağı. */
    private static final class StubTokens implements ServiceTokenPort {

        private final List<String> requestedAudiences = new ArrayList<>();
        private boolean configured = true;
        private boolean failing;

        @Override
        public boolean isConfigured() {
            return configured;
        }

        @Override
        public String tokenFor(String audience) {
            if (failing) {
                throw new ServiceTokenUnavailableException("no token");
            }
            requestedAudiences.add(audience);
            return TOKEN;
        }
    }
}

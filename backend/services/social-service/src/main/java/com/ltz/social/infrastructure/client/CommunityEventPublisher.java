package com.ltz.social.infrastructure.client;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.ltz.social.application.port.EventDeliveryException;
import com.ltz.social.application.port.EventEnvelope;
import com.ltz.social.application.port.ServiceTokenPort;
import com.ltz.social.application.port.SocialEventPublisher;
import com.ltz.social.domain.exception.ServiceTokenUnavailableException;
import com.ltz.social.infrastructure.config.ServiceClientProperties;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Engel olaylarını ({@code UserBlocked}, {@code UserUnblocked}) community-service'in {@code POST /internal/events}
 * ucuna iletir. Her çağrı Auth'tan alınan, hedefe bağlı (aud=community-service) 60 sn'lik servis token'ıyla yapılır
 * ({@code scope=events:ingest}, {@code sub=svc:social-service}). Auth'ta başka bir (social → hedef) çifti tanımlı
 * olmadığından diğer olay türleri teslim edilemez ve {@link EventDeliveryException} ile outbox'ta bekler; hedef
 * tanımlanınca buraya yönlendirme eklenir. Zaman aşımı/yönlendirme ayarları {@code spring.http.clients.*} ile gelir;
 * yeniden deneme relay'dedir. Loglara/iletilere token, gizli anahtar, yük ya da yanıt gövdesi yazılmaz.
 */
public class CommunityEventPublisher implements SocialEventPublisher {

    static final String EVENTS_PATH = "/internal/events";
    static final String COMMUNITY_AUDIENCE = "community-service";
    /** Community'ye iletilen olay türleri; Auth yalnız social → community çiftini tanımlıdır. */
    static final Set<String> COMMUNITY_EVENT_TYPES = Set.of("UserBlocked", "UserUnblocked");

    /** Community'nin beklediği zarf (docs/architecture.md §6.2); {@code payload} iç içe JSON nesnesidir. */
    record OutboundEvent(UUID eventId, String eventType, int eventVersion, Instant occurredAt, String producer,
                         UUID aggregateId, JsonNode payload) {
    }

    private final RestClient restClient;
    private final ServiceTokenPort serviceTokens;
    private final ObjectMapper objectMapper;

    public CommunityEventPublisher(RestClient.Builder builder, ServiceTokenPort serviceTokens,
                                   ObjectMapper objectMapper, ServiceClientProperties properties) {
        this.restClient = builder.clone().baseUrl(properties.communityServiceUrl()).build();
        this.serviceTokens = serviceTokens;
        this.objectMapper = objectMapper;
    }

    /** Gizli anahtar yoksa hiçbir olay denenmez; deneme hakkı yanmaz. */
    @Override
    public boolean isReady() {
        return serviceTokens.isConfigured();
    }

    @Override
    public void publish(EventEnvelope envelope) {
        if (!COMMUNITY_EVENT_TYPES.contains(envelope.eventType())) {
            throw new EventDeliveryException("No delivery target for this event type");
        }
        OutboundEvent body = toBody(envelope);
        String token;
        try {
            token = serviceTokens.tokenFor(COMMUNITY_AUDIENCE);
        } catch (ServiceTokenUnavailableException exception) {
            throw new EventDeliveryException("Service token is unavailable", exception);
        }
        try {
            var response = restClient.post()
                    .uri(EVENTS_PATH)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            // Yönlendirme izlenmez; 2xx dışındaki her yanıt (3xx dahil) teslim sayılmaz.
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new EventDeliveryException("Community answered with HTTP " + response.getStatusCode().value());
            }
        } catch (RestClientException exception) {
            throw new EventDeliveryException("Community delivery failed: " + exception.getClass().getSimpleName(),
                    exception);
        }
    }

    private OutboundEvent toBody(EventEnvelope envelope) {
        try {
            return new OutboundEvent(envelope.eventId(), envelope.eventType(), envelope.eventVersion(),
                    envelope.occurredAt(), envelope.producer(), envelope.aggregateId(),
                    objectMapper.readTree(envelope.payload()));
        } catch (JacksonException exception) {
            throw new EventDeliveryException("Event payload is not valid JSON", exception);
        }
    }
}

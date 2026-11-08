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

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Olayları bir tüketici servisin {@code POST /internal/events} ucuna iletir. Her çağrı Auth'tan alınan, hedefe bağlı
 * ({@code aud=<hedef>}) 60 sn'lik servis token'ıyla yapılır ({@code scope=events:ingest}, {@code sub=svc:social-service}).
 * Hedefin işlemediği türler {@link EventDeliveryException} ile reddedilir. Zaman aşımı/yönlendirme ayarları
 * {@code spring.http.clients.*} ile gelir; yeniden deneme relay'dedir. Loglara/iletilere token, gizli anahtar, yük ya
 * da yanıt gövdesi yazılmaz.
 */
public class ServiceEventPublisher implements SocialEventPublisher {

    static final String EVENTS_PATH = "/internal/events";

    /** Tüketicinin beklediği zarf (docs/architecture.md §6.2); {@code payload} iç içe JSON nesnesidir. */
    record OutboundEvent(UUID eventId, String eventType, int eventVersion, Instant occurredAt, String producer,
                         UUID aggregateId, JsonNode payload) {
    }

    private final RestClient restClient;
    private final ServiceTokenPort serviceTokens;
    private final ObjectMapper objectMapper;
    private final String audience;
    private final Set<String> eventTypes;

    protected ServiceEventPublisher(RestClient.Builder builder, ServiceTokenPort serviceTokens,
                                    ObjectMapper objectMapper, String baseUrl, String audience,
                                    Set<String> eventTypes) {
        this.restClient = builder.clone().baseUrl(baseUrl).build();
        this.serviceTokens = serviceTokens;
        this.objectMapper = objectMapper;
        this.audience = audience;
        this.eventTypes = Set.copyOf(eventTypes);
    }

    /** Bu hedef verilen olay türünü işliyor mu. */
    public boolean handles(String eventType) {
        return eventTypes.contains(eventType);
    }

    /** Gizli anahtar yoksa hiçbir olay denenmez; deneme hakkı yanmaz. */
    @Override
    public boolean isReady() {
        return serviceTokens.isConfigured();
    }

    @Override
    public void publish(EventEnvelope envelope) {
        if (!handles(envelope.eventType())) {
            throw new EventDeliveryException("No delivery target for this event type");
        }
        OutboundEvent body = toBody(envelope);
        String token;
        try {
            token = serviceTokens.tokenFor(audience);
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
                throw new EventDeliveryException(audience + " answered with HTTP " + response.getStatusCode().value());
            }
        } catch (RestClientException exception) {
            throw new EventDeliveryException(audience + " delivery failed: " + exception.getClass().getSimpleName(),
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

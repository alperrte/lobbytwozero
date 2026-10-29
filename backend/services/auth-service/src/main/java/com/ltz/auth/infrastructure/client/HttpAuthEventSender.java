package com.ltz.auth.infrastructure.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import com.ltz.auth.application.port.AuthEventDeliveryException;
import com.ltz.auth.application.port.AuthEventEnvelope;
import com.ltz.auth.application.port.AuthEventSenderPort;
import com.ltz.auth.infrastructure.config.DeletionProperties;
import com.ltz.auth.infrastructure.config.EventProperties;
import com.ltz.auth.infrastructure.security.ServiceTokenIssuer;
import com.ltz.auth.infrastructure.security.TokenClaims;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * Auth → hedef servis olay teslimi: {@code POST <SERVİS>_SERVICE_URL/internal/events}, her çağrıda yeni servis token'ıyla
 * ({@code typ=svc+jwt}, {@code sub=svc:auth-service}, {@code aud=<servis>-service}, {@code scope=events:ingest},
 * {@code account_id=olayın hesabı}). Yönlendirme izlenmez (token başka adrese gitmez); yanıt gövdesi okunmaz; token ve
 * olay verisi loglanmaz. Yalnız {@code 2xx} teslim sayılır.
 */
@Slf4j
@Component
public class HttpAuthEventSender implements AuthEventSenderPort {

    static final String NOT_CONFIGURED = "EVENT_NOT_CONFIGURED";
    static final String UNREACHABLE = "EVENT_UNREACHABLE";
    static final String FAILED = "EVENT_FAILED";
    static final String HTTP_STATUS_PREFIX = "EVENT_HTTP_";
    private static final String[] EVENTS_PATH_SEGMENTS = {"internal", "events"};
    private static final String BEARER = "Bearer ";

    private final HttpClient httpClient;
    private final RestClient restClient;
    private final ServiceTokenIssuer serviceTokens;
    private final Map<String, URI> serviceUrls;

    public HttpAuthEventSender(EventProperties properties, DeletionProperties deletion, ServiceTokenIssuer serviceTokens) {
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(properties.httpTimeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.httpTimeout());
        this.restClient = RestClient.builder().requestFactory(factory).build();
        this.serviceTokens = serviceTokens;
        this.serviceUrls = Map.copyOf(deletion.serviceUrls());
    }

    /** Bağlam kapanırken kendi oluşturduğu HTTP istemcisini kapatır (bağlantı havuzu ve iş parçacıkları kalmaz). */
    @PreDestroy
    void close() {
        httpClient.close();
    }

    @Override
    public void send(String target, AuthEventEnvelope envelope) {
        URI baseUrl = serviceUrls.get(target);
        if (baseUrl == null) {
            throw failure(target, NOT_CONFIGURED);
        }
        String token = serviceTokens.issue(ServiceTokenIssuer.audienceFor(target), TokenClaims.EVENTS_INGEST_SCOPE,
                envelope.aggregateId());
        HttpStatusCode status;
        try {
            status = restClient.post()
                    .uri(eventsUri(baseUrl))
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, BEARER + token)
                    .body(body(envelope))
                    .exchangeForRequiredValue((request, response) -> response.getStatusCode());
        } catch (ResourceAccessException _) {
            throw failure(target, UNREACHABLE);
        } catch (RestClientException _) {
            throw failure(target, FAILED);
        }
        if (!status.is2xxSuccessful()) {
            throw failure(target, HTTP_STATUS_PREFIX + status.value());
        }
    }

    /** Zarfın tüm alanları düz metin/sayı olarak gönderilir (tarih ISO-8601, kimlikler metin). */
    private static Map<String, Object> body(AuthEventEnvelope envelope) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", envelope.eventId().toString());
        body.put("eventType", envelope.eventType());
        body.put("eventVersion", envelope.eventVersion());
        body.put("occurredAt", envelope.occurredAt().toString());
        body.put("producer", envelope.producer());
        body.put("aggregateId", envelope.aggregateId().toString());
        body.put("payload", envelope.payload());
        return body;
    }

    /** Taban adres açılışta şema + host (+ port) olarak doğrulanır; yol buradan eklenir. */
    private static URI eventsUri(URI baseUrl) {
        return UriComponentsBuilder.newInstance()
                .scheme(baseUrl.getScheme())
                .host(baseUrl.getHost())
                .port(baseUrl.getPort())
                .pathSegment(EVENTS_PATH_SEGMENTS)
                .build()
                .encode()
                .toUri();
    }

    private static AuthEventDeliveryException failure(String target, String code) {
        log.debug("Auth event not delivered target={} code={}", target, code);
        return new AuthEventDeliveryException(code);
    }
}

package com.ltz.library.infrastructure.client;

import com.ltz.library.application.port.LibrarySummaryPublisher;
import com.ltz.library.domain.exception.SummaryDeliveryException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@code LibrarySummaryChanged} olayını User'ın {@code POST /internal/events} ucuna iletir (sözleşme: docs/api.md → USER).
 * Auth'tan {@code aud=user-service}, {@code scope=events:ingest} servis token'ı alır; {@code sub=svc:library-service} olduğu için
 * zarftaki {@code producer} aynıdır. Sayılar mutlaktır; {@code occurredAt} gönderim anıdır ve User'da eski olay yeniyi ezmez.
 * Yanıt gövdesi, token ve yük günlüğe veya hata mesajına yazılmaz; retry relay'dedir.
 */
@Component
public class UserServiceSummaryPublisher implements LibrarySummaryPublisher {
    static final String EVENTS_PATH = "/internal/events";
    static final String EVENT_TYPE = "LibrarySummaryChanged";
    static final String PRODUCER = "library-service";
    static final String EVENTS_INGEST_SCOPE = "events:ingest";

    /** Tüketicinin beklediği zarf (docs/architecture.md §6.2); zarfta tanımsız alan reddedilir. */
    record Envelope(UUID eventId, String eventType, int eventVersion, Instant occurredAt, String producer,
            UUID aggregateId, Payload payload) { }

    record Payload(UUID userId, int collectionCount, int completedCount, int favoriteCount) { }

    private final RestClient client;
    private final AuthServiceTokenClient tokens;
    private final Clock clock;

    @Autowired
    public UserServiceSummaryPublisher(RestClient.Builder builder, AuthServiceTokenClient tokens,
            @Value("${ltz.library.user.base-url:}") String baseUrl) {
        this(builder, tokens, baseUrl, Clock.systemUTC());
    }

    UserServiceSummaryPublisher(RestClient.Builder builder, AuthServiceTokenClient tokens, String baseUrl, Clock clock) {
        this.client = baseUrl == null || baseUrl.isBlank() ? null : builder.baseUrl(baseUrl.strip()).build();
        this.tokens = tokens;
        this.clock = clock;
    }

    @Override
    public boolean ready() { return client != null && tokens.configured(); }

    @Override
    public void publish(UUID userId, int collectionCount, int completedCount, int favoriteCount) {
        if (client == null) throw new SummaryDeliveryException("User adresi tanımlı değil");
        Optional<String> token = tokens.token(AuthServiceTokenClient.USER_AUDIENCE, EVENTS_INGEST_SCOPE);
        if (token.isEmpty()) throw new SummaryDeliveryException("Servis token'ı alınamadı");
        Instant occurredAt = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        var envelope = new Envelope(UUID.randomUUID(), EVENT_TYPE, 1, occurredAt, PRODUCER, userId,
                new Payload(userId, collectionCount, completedCount, favoriteCount));
        try {
            var response = client.post().uri(EVENTS_PATH).contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(token.get())).body(envelope).retrieve().toBodilessEntity();
            // Yönlendirme izlenmez; 2xx dışındaki her yanıt teslim sayılmaz.
            if (!response.getStatusCode().is2xxSuccessful())
                throw new SummaryDeliveryException("User HTTP " + response.getStatusCode().value() + " döndü");
        } catch (RestClientException error) {
            throw new SummaryDeliveryException("User teslimi başarısız: " + error.getClass().getSimpleName(), error);
        }
    }
}
